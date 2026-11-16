import { useCallback, useEffect, useState } from 'react'
import QRCode from 'qrcode'
import { Link } from 'react-router-dom'
import { CHECK_IN_QR_OPTIONS, checkInQrApi } from '../../api/checkInQrApi'
import { getErrorMessage } from '../../api/errors'
import { Button, Card, Notice, buttonClassName } from '../../components/ui'
import { useResource } from '../../hooks/useResource'
import {
  MemberCommerceCard,
  MemberCommerceHeader,
  MemberCommerceHero,
} from './MemberCommerceComponents'

/** Fixed check-in QR: the content is the member code, so it never changes or expires. */
export function CheckInQrPage() {
  const load = useCallback((signal: AbortSignal) => checkInQrApi.mine(signal), [])
  const [qr, reload] = useResource(load)
  const [image, setImage] = useState<{ content: string; url: string } | null>(null)
  const [renderError, setRenderError] = useState<string | null>(null)
  const content = qr.status === 'ready' ? qr.data.qrContent : null

  useEffect(() => {
    if (!content) return
    let active = true
    QRCode.toDataURL(content, CHECK_IN_QR_OPTIONS)
      .then((url) => {
        if (active) {
          setImage({ content, url })
          setRenderError(null)
        }
      })
      .catch((failure: unknown) => {
        if (active) setRenderError(getErrorMessage(failure))
      })
    return () => {
      active = false
    }
  }, [content])

  const imageUrl = image && image.content === content ? image.url : null

  return (
    <div className="page member-commerce">
      <MemberCommerceHeader
        eyebrow="Member Workspace"
        title="Check-in"
        highlight="QR"
        description="Show this QR code at Reception to check in."
        actions={
          <Link className={buttonClassName('secondary')} to="/member/profile">
            My profile
          </Link>
        }
      />
      <MemberCommerceHero
        kicker="SPORTIFY FAST ARRIVAL"
        title="YOUR CHECK-IN QR."
        highlight="ALWAYS THE SAME."
        description="One fixed QR code per member, used only for check-in at the Sportify reception desk."
      />

      <div className="member-commerce-grid">
        <div className="member-commerce-column">
          <Card title="Check-in QR">
            <p style={{ margin: '0 0 var(--space-3)', color: 'var(--color-text-muted)', fontSize: 'var(--text-sm)' }}>
              This QR records arrival through Reception only. It cannot be used to sign in or pay.
            </p>
            {qr.status === 'loading' && <p role="status">Loading your check-in QR...</p>}
            {qr.status === 'error' && (
              <Notice tone="danger" live="assertive">
                {getErrorMessage(qr.error)}{' '}
                <Button type="button" variant="secondary" size="sm" onClick={reload}>
                  Retry
                </Button>
              </Notice>
            )}
            {renderError && (
              <Notice tone="danger" live="assertive">
                {renderError}
              </Notice>
            )}

            {qr.status === 'ready' && (
              <div className="member-commerce-qr-box" style={{ margin: 'var(--space-4) 0' }}>
                <span className="member-commerce-kpi__label">Member code</span>
                <p
                  className="member-commerce-qr-box__code"
                  style={{ fontSize: '2rem', fontWeight: 700, margin: 'var(--space-2) 0' }}
                >
                  {qr.data.memberCode}
                </p>
                {imageUrl && (
                  <>
                    <div className="member-commerce-qr-box__frame">
                      <img
                        src={imageUrl}
                        width={320}
                        height={320}
                        alt="Member check-in QR"
                        style={{ display: 'block' }}
                      />
                    </div>
                    <a
                      className={buttonClassName('secondary')}
                      href={imageUrl}
                      download={`sportify-check-in-${qr.data.memberCode}.png`}
                      style={{ marginTop: 'var(--space-4)' }}
                    >
                      Download QR
                    </a>
                  </>
                )}
              </div>
            )}
            <p style={{ marginTop: 'var(--space-4)', fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
              Reception can also type your Member code if the QR cannot be scanned.
            </p>
          </Card>
        </div>

        <div className="member-commerce-column">
          <MemberCommerceCard title="Check-In Guidelines">
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
              <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start' }}>
                <span className="member-commerce-badge member-commerce-badge--info" aria-hidden="true">
                  1
                </span>
                <p style={{ margin: 0, fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
                  Your QR code never changes. Download it or keep this page handy.
                </p>
              </div>
              <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start' }}>
                <span className="member-commerce-badge member-commerce-badge--info" aria-hidden="true">
                  2
                </span>
                <p style={{ margin: 0, fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
                  Show the QR to the reception scanner on arrival. Only active accounts can check in.
                </p>
              </div>
              <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start' }}>
                <span className="member-commerce-badge member-commerce-badge--neutral" aria-hidden="true">
                  3
                </span>
                <p style={{ margin: 0, fontSize: 'var(--text-xs)', color: 'var(--color-text-muted)' }}>
                  Class and court attendance is marked separately by your coach upon session commencement.
                </p>
              </div>
            </div>
          </MemberCommerceCard>
        </div>
      </div>
    </div>
  )
}
