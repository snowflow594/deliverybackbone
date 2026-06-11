import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'

/**
 * Suscripción STOMP a /topic/inventory: cada cambio de stock en el backend
 * (reserva, venta, expiración, restock) llega aquí en tiempo real.
 */
export function useInventoryStream(onEvent) {
  const [connected, setConnected] = useState(false)
  const handler = useRef(onEvent)
  handler.current = onEvent

  useEffect(() => {
    const client = new Client({
      brokerURL: `ws://${window.location.hostname}:8080/ws`,
      reconnectDelay: 2000,
      onConnect: () => {
        setConnected(true)
        client.subscribe('/topic/inventory', (message) => {
          handler.current(JSON.parse(message.body))
        })
      },
      onWebSocketClose: () => setConnected(false),
    })
    client.activate()
    return () => client.deactivate()
  }, [])

  return connected
}
