/*
 * Raw color scanner for the UI-00 color freeze. Finds every color literal in stylesheets, scripts and HTML so a
 * test can compare it with the reviewed baseline in rawColorBaseline.json. Used only by tests.
 *
 * Stylesheets: hex, functional syntaxes (rgb, rgba, hsl, hsla, hwb, lab, lch, oklab, oklch, color) and CSS named
 * colors in declaration values, keyed by the enclosing rules, property and color
 * (".hero { color|#ffffff", "@media (max-width: 960px) { .hero { color|#ffffff") so changing a value, moving it to
 * another property or swapping colors between two selectors is a new key.
 * Scripts and HTML: hex and functional colors inside string literals and attribute values, plus named colors where
 * the string is a CSS color value (an inline style string, a style object key such as color, backgroundColor or
 * textColor, an SVG fill or stroke attribute, style.color = or setProperty, also as a branch of ?:, && or ||).
 * Anywhere else words like "red" or "tan" are ordinary text. <style> blocks in HTML are scanned as stylesheets.
 * Hex-like link targets and selectors ("#abc" in href, to, id or querySelector, "/page#add") are not colors.
 */

export type ColorCounts = Record<string, number>
export type ColorBaseline = Record<string, ColorCounts>

/* Not after a word, path or query character, so "/page#add" and "?tab=#bad" are not colors. */
const HEX = /(?<![\w/?=&.-])#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3,4})(?![\w-])/g
const FUNCTION_START = /(?<![\w-])(rgba?|hsla?|hwb|lab|lch|oklab|oklch|color)\(/gi

/* CSS properties that take a color, in kebab-case (style object keys are converted from camelCase first). */
const COLOR_PROPERTY = /^(?:--[\w-]+|(?:-webkit-)?(?:color|background(?:-color|-image)?|border(?:-(?:top|right|bottom|left|block|inline)(?:-(?:start|end))?)?(?:-color)?|outline(?:-color)?|fill|stroke|stop-color|flood-color|lighting-color|caret-color|accent-color|column-rule(?:-color)?|text-decoration(?:-color)?|text-emphasis(?:-color)?|text-shadow|box-shadow|scrollbar-color))$/

/* Text right before a string that makes it a link target or a selector, not a color. */
const LINK_CONTEXT = /(?:\b(?:href|to|hash|id|htmlFor|for|xlinkHref|xlink:href)\s*[=:]\s*\{?|\b(?:querySelector|querySelectorAll|getElementById|closest|matches|navigate|scrollIntoView)\(\s*)$/

/*
 * Text right before a string that makes it the value of a color property; group 1 or 2 is the property. The value may
 * be one branch of a condition (color: active ? 'red' : 'blue', background: disabled && 'gray', 'x' ?? 'navy').
 */
const PROPERTY_CONTEXT = /(?:(?:^|[\s{,;(.])['"]?([A-Za-z-]+)['"]?\s*[:=]\s*\{?\s*(?:(?:[^;{},\n'"`]|'[^'\n]*'|"[^"\n]*")*?(?:\?|:|\|\||&&|\?\?)\s*)?|\bsetProperty\(\s*['"]([-\w]+)['"]\s*,\s*)$/

/* <style> blocks of an HTML file, scanned as stylesheets. */
const STYLE_BLOCK = /<style\b[^>]*>([\s\S]*?)<\/style>/gi

/* CSS named colors (CSS Color 4), without transparent and currentcolor, which carry no palette value. */
const NAMED_COLORS = new Set(
  ('aliceblue antiquewhite aqua aquamarine azure beige bisque black blanchedalmond blue blueviolet brown burlywood ' +
    'cadetblue chartreuse chocolate coral cornflowerblue cornsilk crimson cyan darkblue darkcyan darkgoldenrod darkgray ' +
    'darkgreen darkgrey darkkhaki darkmagenta darkolivegreen darkorange darkorchid darkred darksalmon darkseagreen ' +
    'darkslateblue darkslategray darkslategrey darkturquoise darkviolet deeppink deepskyblue dimgray dimgrey dodgerblue ' +
    'firebrick floralwhite forestgreen fuchsia gainsboro ghostwhite gold goldenrod gray green greenyellow grey honeydew ' +
    'hotpink indianred indigo ivory khaki lavender lavenderblush lawngreen lemonchiffon lightblue lightcoral lightcyan ' +
    'lightgoldenrodyellow lightgray lightgreen lightgrey lightpink lightsalmon lightseagreen lightskyblue lightslategray ' +
    'lightslategrey lightsteelblue lightyellow lime limegreen linen magenta maroon mediumaquamarine mediumblue ' +
    'mediumorchid mediumpurple mediumseagreen mediumslateblue mediumspringgreen mediumturquoise mediumvioletred ' +
    'midnightblue mintcream mistyrose moccasin navajowhite navy oldlace olive olivedrab orange orangered orchid ' +
    'palegoldenrod palegreen paleturquoise palevioletred papayawhip peachpuff peru pink plum powderblue purple ' +
    'rebeccapurple red rosybrown royalblue saddlebrown salmon sandybrown seagreen seashell sienna silver skyblue ' +
    'slateblue slategray slategrey snow springgreen steelblue tan teal thistle tomato turquoise violet wheat white ' +
    'whitesmoke yellow yellowgreen').split(' '),
)

/** Lower case without whitespace, so formatting changes do not count as new colors. */
function normalize(literal: string): string {
  return literal.toLowerCase().replace(/\s+/g, '')
}

/** Text of each balanced function call such as rgba(var(--x), 0.5) starting at the matches of FUNCTION_START. */
function functionColors(text: string): string[] {
  const found: string[] = []
  for (const match of text.matchAll(FUNCTION_START)) {
    let depth = 0
    for (let i = match.index + match[0].length - 1; i < text.length; i += 1) {
      if (text[i] === '(') depth += 1
      if (text[i] === ')') depth -= 1
      if (depth === 0) {
        found.push(text.slice(match.index, i + 1))
        break
      }
    }
  }
  return found
}

function add(counts: ColorCounts, key: string) {
  counts[key] = (counts[key] ?? 0) + 1
}

/** Color literals in a declaration value: functions first, then hex and named colors outside them. */
function valueColors(value: string, named: boolean): string[] {
  const functions = functionColors(value)
  let rest = value
  for (const call of functions) rest = rest.replace(call, ' ')
  const hex = rest.match(HEX) ?? []
  rest = rest.replace(HEX, ' ')
  const words = named ? rest.replace(/--[\w-]+/g, ' ').match(/(?<![\w-])[a-zA-Z]+(?![\w-])/g) ?? [] : []
  return [...functions, ...hex, ...words.filter((word) => NAMED_COLORS.has(word.toLowerCase()))].map(normalize)
}

/** Single spaced rule prelude, so reformatting a selector does not count as a new key. */
function prelude(text: string): string {
  return text.trim().replace(/\s+/g, ' ')
}

/** Color literals of a stylesheet, keyed by "enclosing rules { property|color". */
export function cssColors(css: string): ColorCounts {
  const counts: ColorCounts = {}
  const clean = css.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/url\((?:[^()'"]|'[^']*'|"[^"]*")*\)/gi, 'url()')
  const rules: string[] = []
  let start = 0
  for (const match of clean.matchAll(/[{};]/g)) {
    const segment = clean.slice(start, match.index)
    start = match.index + 1
    if (match[0] === '{') {
      rules.push(prelude(segment))
      continue
    }
    const declaration = /^\s*([-\w]+)\s*:\s*([\s\S]+?)\s*$/.exec(segment)
    if (declaration && rules.length) {
      const scope = rules.join(' { ')
      for (const color of valueColors(declaration[2], true)) add(counts, `${scope} { ${declaration[1].toLowerCase()}|${color}`)
    }
    if (match[0] === '}') rules.pop()
  }
  return counts
}

/** "backgroundColor" to "background-color"; kebab-case and custom properties are kept. */
function kebab(property: string): string {
  return property.startsWith('--') ? property : property.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`).toLowerCase()
}

/** Whether a script string is a CSS color value, judged by the property written right before it. */
function isColorValue(before: string): boolean {
  const match = PROPERTY_CONTEXT.exec(before)
  const property = match?.[1] ?? match?.[2]
  return property !== undefined && (COLOR_PROPERTY.test(kebab(property)) || /colou?r$/i.test(property))
}

/** Named colors in the color declarations of an inline style or CSS string such as "color: red; fill: navy". */
function inlineNamedColors(text: string): string[] {
  const found: string[] = []
  for (const match of text.matchAll(/(?:^|[;{])\s*([-\w]+)\s*:\s*([^;{}]+)/g)) {
    if (COLOR_PROPERTY.test(match[1].toLowerCase())) {
      found.push(...valueColors(match[2], true).filter((color) => /^[a-z]+$/.test(color)))
    }
  }
  return found
}

/** Color literals inside string literals and attribute values of a script or HTML file, and its <style> blocks. */
export function scriptColors(input: string): ColorCounts {
  const counts: ColorCounts = {}
  for (const block of input.matchAll(STYLE_BLOCK)) {
    for (const [key, count] of Object.entries(cssColors(block[1]))) counts[`<style> ${key}`] = (counts[`<style> ${key}`] ?? 0) + count
  }
  const source = input.replace(STYLE_BLOCK, '<style></style>')
  for (const match of source.matchAll(/(['"`])((?:\\.|(?!\1)[^\\])*)\1/g)) {
    const before = source.slice(Math.max(0, match.index - 80), match.index)
    const text = match[2]
    if (LINK_CONTEXT.test(before)) {
      for (const color of functionColors(text)) add(counts, normalize(color))
      continue
    }
    const colors = isColorValue(before) ? valueColors(text, true) : [...valueColors(text, false), ...inlineNamedColors(text)]
    for (const color of colors) add(counts, color)
  }
  return counts
}

/** Every key whose count is above the baseline (new colors, changed values, colors moved to a new file). */
export function colorViolations(actual: ColorBaseline, baseline: ColorBaseline): string[] {
  const violations: string[] = []
  for (const [file, counts] of Object.entries(actual)) {
    for (const [key, count] of Object.entries(counts)) {
      const allowed = baseline[file]?.[key] ?? 0
      if (count > allowed) violations.push(`${file}: ${key} (${count} found, ${allowed} allowed)`)
    }
  }
  return violations.sort()
}
