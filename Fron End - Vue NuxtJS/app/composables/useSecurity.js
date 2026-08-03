/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
export const useSecurity = () => {
  const protectXSS = (value, options = {}) => {
    if (value == null) return ''

    return DOMPurify.sanitize(String(value), options)
  }

  const protectStrictXSS = (value) => {
    if (value == null) return ''

    return DOMPurify.sanitize(String(value), {
      ALLOWED_TAGS: [],
      ALLOWED_ATTR: []
    })
  }

  return {
    protectXSS,
    protectStrictXSS
  }
}
