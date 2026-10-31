"""Fail-closed helpers for the QR-07 evidence harness (no application behavior)."""
import re


TABLES = (
    'payment', 'invoice', 'invoice_line', 'member_card', 'sport_package_registration',
    'refund_request', 'cash_event', 'cash_capture_ticket', 'cash_coverage',
    'simulated_payment_transaction', 'simulated_qr_session', 'activity_log',
)


def validate_fingerprints(values):
    if set(values) != set(TABLES) or any(
        not isinstance(v, str) or not re.fullmatch(r'[A-Fa-f0-9]{64}', v)
        for v in values.values()
    ):
        raise ValueError('Missing table or invalid SQL SHA-256 output')
    return values


def constraint_rejected(result, expected):
    return result.get('errorNumber') == 547 and expected in result.get('errorMessage', '')


def correlation_observation(client, server, require_echo=False):
    if client is not None and not isinstance(client, str):
        raise ValueError('Invalid client correlation ID')
    if server is not None and (not isinstance(server, str) or not server.strip()):
        raise ValueError('Invalid server correlation ID')
    if require_echo and (client is None or server != client):
        raise ValueError('Required server correlation echo is absent or mismatched')
    return {'clientCorrelationId': client, 'serverCorrelationId': server}


def luminance(rgb):
    def linear(c):
        value = c / 255.0
        return value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4
    return sum(w * linear(c) for w, c in zip((0.2126, 0.7152, 0.0722), rgb))


def contrast(a, b):
    first, second = sorted((luminance(a), luminance(b)))
    return (second + 0.05) / (first + 0.05)


def localized_contrast(original, background, black_mask, white_mask, width, threshold=4.5):
    """Compare matching glyph/background coordinates, ignoring antialiased edges.

    Black/white text-only paints identify >= 98% opaque glyph interiors. The
    original image supplies the actual text color (including any gradient);
    visibility:hidden supplies its local underlying background. No crop extrema.
    This is rendered evidence, not blanket WCAG certification.
    """
    count = len(original)
    if width <= 0 or not count or count % width or any(
        len(pixels) != count for pixels in (background, black_mask, white_mask)
    ):
        raise ValueError('Inconsistent contrast image geometry')
    samples = []
    for index in range(count):
        if min(w - b for b, w in zip(black_mask[index], white_mask[index])) >= 250:
            ratio = contrast(original[index], background[index])
            samples.append({'x': index % width, 'y': index // width,
                            'foregroundRgb': list(original[index]),
                            'backgroundRgb': list(background[index]), 'ratio': ratio})
    if len(samples) < 10:
        raise ValueError('Insufficient opaque glyph pixels; contrast NOT VERIFIED')
    minimum = min(s['ratio'] for s in samples)
    return {'minimumLocalContrastRatio': minimum, 'threshold': threshold,
            'passed': minimum >= threshold, 'sampleCount': len(samples), 'samples': samples}
