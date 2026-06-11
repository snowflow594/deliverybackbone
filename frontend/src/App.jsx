import { useCallback, useEffect, useRef, useState } from 'react'
import { useInventoryStream } from './useInventoryStream.js'

// Sin auth todavía (fase posterior): compra como el cliente demo del seed.
const DEMO_USER_ID = 2
const DELIVERY = {
  deliveryLat: -12.0931,
  deliveryLng: -77.0465,
  deliveryAddress: 'Av. Larco 123, Miraflores',
  district: 'Miraflores',
}

const REASON_LABELS = {
  RESERVED: 'reservado',
  SOLD: 'vendido',
  RELEASED: 'liberado',
  RESTOCKED: 'repuesto',
}

export default function App() {
  const [products, setProducts] = useState([])
  const [events, setEvents] = useState([])
  const [flash, setFlash] = useState({})
  const [notice, setNotice] = useState(null)
  const flashTimers = useRef({})

  useEffect(() => {
    fetch('/api/products')
      .then((r) => r.json())
      .then(setProducts)
      .catch(() => setNotice('No se pudo cargar el catálogo. ¿Backend corriendo en :8080?'))
  }, [])

  const onStockEvent = useCallback((event) => {
    setProducts((prev) =>
      prev.map((p) =>
        p.id === event.productId ? { ...p, stockAvailable: event.stockAvailable } : p,
      ),
    )
    setEvents((prev) => [event, ...prev].slice(0, 12))
    setFlash((prev) => ({ ...prev, [event.productId]: event.reason }))
    clearTimeout(flashTimers.current[event.productId])
    flashTimers.current[event.productId] = setTimeout(() => {
      setFlash((prev) => {
        const next = { ...prev }
        delete next[event.productId]
        return next
      })
    }, 1200)
  }, [])

  const connected = useInventoryStream(onStockEvent)

  async function buy(productId) {
    setNotice(null)
    const res = await fetch('/api/orders/checkout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: DEMO_USER_ID,
        items: [{ productId, quantity: 1 }],
        ...DELIVERY,
      }),
    })
    if (res.status === 409) {
      setNotice('Sin stock disponible: otro comprador ganó la carrera.')
    } else if (!res.ok) {
      setNotice(`Error inesperado (HTTP ${res.status})`)
    }
    // No actualizamos el stock a mano: llega solo por WebSocket.
  }

  return (
    <main>
      <header>
        <h1>DeliveryBackbone</h1>
        <span className={connected ? 'badge live' : 'badge off'}>
          {connected ? '● stock en vivo' : '○ desconectado'}
        </span>
      </header>
      <p className="hint">
        Abre esta página en dos pestañas y compra en una: el stock cambia en la otra al instante.
      </p>

      {notice && <div className="notice">{notice}</div>}

      <section className="grid">
        {products.map((p) => (
          <article key={p.id} className={`card ${flash[p.id] ? 'flash-' + flash[p.id] : ''}`}>
            <h2>{p.name}</h2>
            <div className="meta">
              <span className="price">S/ {Number(p.price).toFixed(2)}</span>
              <span className={`stock ${p.stockAvailable === 0 ? 'out' : ''}`}>
                {p.stockAvailable === 0 ? 'AGOTADO' : `${p.stockAvailable} disp.`}
              </span>
            </div>
            <button onClick={() => buy(p.id)} disabled={p.stockAvailable === 0}>
              Comprar 1
            </button>
          </article>
        ))}
      </section>

      <section className="events">
        <h3>Eventos en vivo (Redis pub/sub → STOMP)</h3>
        {events.length === 0 && <p className="hint">Aún no hay eventos. Compra algo…</p>}
        <ul>
          {events.map((e, i) => (
            <li key={i}>
              <code>#{e.productId}</code> {REASON_LABELS[e.reason] ?? e.reason} → quedan{' '}
              <strong>{e.stockAvailable}</strong>
              <span className="time">{new Date(e.at).toLocaleTimeString()}</span>
            </li>
          ))}
        </ul>
      </section>
    </main>
  )
}
