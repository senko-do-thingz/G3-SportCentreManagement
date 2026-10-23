/** Ids used by a tab and its panel, exported so tests and links can target them. */
export function tabIds(prefix: string, id: string) {
  return { tab: `${prefix}-tab-${id}`, panel: `${prefix}-panel-${id}` }
}
