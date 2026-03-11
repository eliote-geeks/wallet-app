import type { SubscriberArgs, SubscriberConfig } from "@medusajs/framework"
import { ContainerRegistrationKeys } from "@medusajs/framework/utils"
import crypto from "crypto"

const WEBHOOK_URL = process.env.KOBO_BACKEND_WEBHOOK_URL
const WEBHOOK_SECRET = process.env.KOBO_BACKEND_WEBHOOK_SECRET
const WEBHOOK_TIMEOUT_MS = Number.parseInt(process.env.KOBO_BACKEND_WEBHOOK_TIMEOUT_MS || "5000", 10)

const ORDER_FIELDS = [
  "id",
  "currency_code",
  "total",
  "status",
  "payment_status",
  "fulfillment_status",
  "metadata",
  "items.id",
  "items.title",
  "items.product_id",
  "items.quantity",
  "items.unit_price",
  "items.total",
  "items.metadata",
  "items.product.id",
  "items.product.metadata",
  "items.variant.id",
  "items.variant.product_id",
  "items.variant.product.metadata",
] as const

function signPayload(body: string, secret: string) {
  return crypto.createHmac("sha256", secret).update(body).digest("hex")
}

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function postWebhook(eventName: string, body: string) {
  if (!WEBHOOK_URL) {
    return
  }

  const headers: Record<string, string> = {
    "content-type": "application/json",
    "x-medusa-event": eventName,
  }

  if (WEBHOOK_SECRET) {
    headers["x-medusa-signature"] = `sha256=${signPayload(body, WEBHOOK_SECRET)}`
  }

  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), WEBHOOK_TIMEOUT_MS)
  try {
    const res = await fetch(WEBHOOK_URL, {
      method: "POST",
      headers,
      body,
      signal: controller.signal,
    })

    if (!res.ok) {
      const text = await res.text().catch(() => "")
      throw new Error(`Webhook failed status=${res.status} body=${text}`)
    }
  } finally {
    clearTimeout(timeout)
  }
}

export default async function koboBackendWebhookSubscriber({
  event,
  container,
}: SubscriberArgs<any>) {
  if (!WEBHOOK_URL) {
    return
  }

  const eventName = event?.name
  if (!eventName) {
    return
  }

  // We only forward order/payment events.
  if (!eventName.startsWith("order.") && !eventName.startsWith("payment.")) {
    return
  }

  let envelope: any = {
    event: eventName,
    data: {},
  }

  if (eventName.startsWith("order.")) {
    const data: any = event?.data || {}
    const orderId = data.id || data.order_id || data.orderId || data.order?.id

    let orderPayload: any = data
    if (orderId) {
      const query = container.resolve(ContainerRegistrationKeys.QUERY)
      const result = await query.graph({
        entity: "order",
        fields: [...ORDER_FIELDS],
        filters: { id: orderId },
      })

      if (result?.data?.length) {
        orderPayload = result.data[0]
      }
    }

    envelope.data.order = orderPayload
  } else {
    // payment.*
    envelope.data.payment = event.data
  }

  const body = JSON.stringify(envelope)

  // Small retry loop to avoid losing events when the backend is restarting.
  const attempts = 3
  for (let attempt = 1; attempt <= attempts; attempt++) {
    try {
      await postWebhook(eventName, body)
      return
    } catch (err) {
      if (attempt >= attempts) {
        throw err
      }
      await sleep(500 * attempt)
    }
  }
}

export const config: SubscriberConfig = {
  event: [
    "order.placed",
    "order.payment_captured",
    "order.completed",
    "order.canceled",
    "order.cancelled",
    "order.refunded",
    "payment.captured",
    "payment.failed",
  ],
}
