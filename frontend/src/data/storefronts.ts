/** 与后端 Storefronts 保持一致。代码是苹果官方 country 参数。 */
export const STOREFRONTS = [
  { code: 'cn', name: '中国大陆' },
  { code: 'hk', name: '中国香港' },
  { code: 'tw', name: '中国台湾' },
  { code: 'us', name: '美国' },
  { code: 'jp', name: '日本' },
  { code: 'kr', name: '韩国' },
  { code: 'sg', name: '新加坡' },
  { code: 'gb', name: '英国' },
  { code: 'de', name: '德国' },
  { code: 'fr', name: '法国' },
  { code: 'au', name: '澳大利亚' },
  { code: 'ca', name: '加拿大' }
] as const

export type StorefrontCode = (typeof STOREFRONTS)[number]['code']

export function storefrontName(code?: string) {
  return STOREFRONTS.find((item) => item.code === code)?.name || '未标记'
}
