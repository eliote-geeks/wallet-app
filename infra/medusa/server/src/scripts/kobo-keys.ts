import type { ExecArgs } from "@medusajs/framework/types"
import {
  ContainerRegistrationKeys,
  Modules,
} from "@medusajs/framework/utils"
import {
  createApiKeysWorkflow,
  createSalesChannelsWorkflow,
  linkSalesChannelsToApiKeyWorkflow,
} from "@medusajs/medusa/core-flows"

export default async function koboKeys({ container }: ExecArgs) {
  const logger = container.resolve(ContainerRegistrationKeys.LOGGER)
  const salesChannelModuleService = container.resolve(Modules.SALES_CHANNEL)

  const name = "Default Sales Channel"

  let channels = await salesChannelModuleService.listSalesChannels({ name })
  if (!channels.length) {
    const { result } = await createSalesChannelsWorkflow(container).run({
      input: {
        salesChannelsData: [{ name }],
      },
    })
    channels = result
  }

  const salesChannelId = channels[0].id

  const { result: publishableApiKeyResult } = await createApiKeysWorkflow(container).run({
    input: {
      api_keys: [
        {
          title: "Webshop",
          type: "publishable",
          created_by: "",
        },
      ],
    },
  })

  const publishableApiKey = publishableApiKeyResult[0]

  await linkSalesChannelsToApiKeyWorkflow(container).run({
    input: {
      id: publishableApiKey.id,
      add: [salesChannelId],
    },
  })

  const { result: secretApiKeyResult } = await createApiKeysWorkflow(container).run({
    input: {
      api_keys: [
        {
          title: "Backend",
          type: "secret",
          created_by: "",
        },
      ],
    },
  })

  const secretApiKey = secretApiKeyResult[0]

  logger.info(`KOBO_MEDUSA_PUBLISHABLE_KEY=${publishableApiKey.token}`)
  logger.info(`KOBO_MEDUSA_ADMIN_TOKEN=${secretApiKey.token}`)
}
