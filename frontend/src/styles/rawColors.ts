/*
 * Raw color scanner for the UI-00 color freeze. Finds every color literal in stylesheets, scripts and HTML so a
 * test can compare it with the reviewed baseline in rawColorBaseline.json. Used only by tests.
 *
 * Stylesheets: hex, functional syntaxes (rgb, rgba, hsl, hsla, hwb, lab, lch, oklab, oklch, color) and CSS named
 * colors in declaration values, keyed by property ("color|#ffffff", "--h-bg|#ffffff") so changing a value or moving
 * it to another property is a new key. Scripts and HTML: hex and functional colors inside string literals and
 * attribute values (named colors are not scanned there because ordinary words like "red" or "tan" are common text).
 */

export type ColorCounts = Record<string, number>
export type ColorBaseline = Record<string, ColorCounts>

const HEX = /#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3,4})(?![\w-])/g
const FUNCTION_START = /(?<![\w-])(rgba?|hsla?|hwb|lab|lch|oklab|oklch|color)\(/gi

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

/** Color literals of a stylesheet, keyed by "property|color". */
export function cssColors(css: string): ColorCounts {
  const counts: ColorCounts = {}
  const clean = css.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/url\((?:[^()'"]|'[^']*'|"[^"]*")*\)/gi, 'url()')
  for (const match of clean.matchAll(/([-\w]+)\s*:\s*([^;{}]+?)\s*(?=;|\})/g)) {
    for (const color of valueColors(match[2], true)) add(counts, `${match[1].toLowerCase()}|${color}`)
  }
  return counts
}

/** Color literals inside string literals and attribute values of a script or HTML file. */
export function scriptColors(source: string): ColorCounts {
  const counts: ColorCounts = {}
  for (const match of source.matchAll(/(['"`])((?:\\.|(?!\1)[^\\])*)\1/g)) {
    for (const color of valueColors(match[2], false)) add(counts, color)
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
