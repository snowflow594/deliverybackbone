import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * LoadSimulator — prueba de concurrencia contra el backend REAL corriendo.
 *
 * Lanza N compradores virtuales (hilos) que hacen checkout del MISMO producto
 * a la vez, y verifica el invariante de cero sobreventa:
 *
 *     disponible_inicial == compras_exitosas * qty + disponible_final
 *
 * Uso (requiere backend en marcha):
 *     java LoadSimulator.java [compradores=50] [qtyPorCompra=1] [productId=1] [userId=2] [baseUrl=http://localhost:8080]
 *
 * Sin dependencias: archivo único, ejecutable directo con Java 21+.
 */
public class LoadSimulator {

    private static final Pattern STOCK_PATTERN = Pattern.compile("\"stockAvailable\"\\s*:\\s*(\\d+)");

    public static void main(String[] args) throws Exception {
        int buyers = args.length > 0 ? Integer.parseInt(args[0]) : 50;
        int qty = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        long productId = args.length > 2 ? Long.parseLong(args[2]) : 1;
        long userId = args.length > 3 ? Long.parseLong(args[3]) : 2;
        String baseUrl = args.length > 4 ? args[4] : "http://localhost:8080";

        HttpClient http = HttpClient.newHttpClient();

        int initialAvailable = fetchAvailable(http, baseUrl, productId);
        System.out.printf("Producto %d — stock disponible inicial: %d%n", productId, initialAvailable);
        System.out.printf("Lanzando %d compradores concurrentes (qty=%d cada uno)...%n%n", buyers, qty);

        String body = """
                {"userId":%d,"items":[{"productId":%d,"quantity":%d}],
                 "deliveryLat":-12.0931,"deliveryLng":-77.0465,
                 "deliveryAddress":"Av. Larco 123, Miraflores","district":"Miraflores"}
                """.formatted(userId, productId, qty);

        var ok = new AtomicInteger();
        var noStock = new AtomicInteger();
        var errors = new AtomicInteger();
        var startGun = new CountDownLatch(1);
        var done = new CountDownLatch(buyers);

        long t0;
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < buyers; i++) {
                pool.submit(() -> {
                    try {
                        startGun.await();
                        var request = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "/api/orders/checkout"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build();
                        int status = http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
                        if (status == 201) ok.incrementAndGet();
                        else if (status == 409) noStock.incrementAndGet();
                        else errors.incrementAndGet();
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            t0 = System.nanoTime();
            startGun.countDown();
            done.await();
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        int finalAvailable = fetchAvailable(http, baseUrl, productId);
        int reservedUnits = ok.get() * qty;

        System.out.println("================ RESULTADOS ================");
        System.out.printf("Compras exitosas (201):      %d%n", ok.get());
        System.out.printf("Rechazos por stock (409):    %d%n", noStock.get());
        System.out.printf("Errores inesperados:         %d%n", errors.get());
        System.out.printf("Tiempo total:                %d ms%n", elapsedMs);
        System.out.printf("Disponible inicial/final:    %d / %d%n", initialAvailable, finalAvailable);
        System.out.println("--------------------------------------------");

        boolean invariantHolds = initialAvailable == reservedUnits + finalAvailable
                && errors.get() == 0;
        if (invariantHolds) {
            System.out.printf("INVARIANTE OK: %d == %d (reservado) + %d (disponible) — CERO SOBREVENTA%n",
                    initialAvailable, reservedUnits, finalAvailable);
        } else {
            System.out.printf("INVARIANTE ROTO: %d != %d + %d%n",
                    initialAvailable, reservedUnits, finalAvailable);
            System.exit(1);
        }
    }

    private static int fetchAvailable(HttpClient http, String baseUrl, long productId) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/products/" + productId))
                .GET()
                .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("No se pudo leer el producto %d (HTTP %d). ¿Backend corriendo?"
                    .formatted(productId, response.statusCode()));
        }
        var matcher = STOCK_PATTERN.matcher(response.body());
        if (!matcher.find()) {
            throw new IllegalStateException("Respuesta sin stockAvailable: " + response.body());
        }
        return Integer.parseInt(matcher.group(1));
    }
}
