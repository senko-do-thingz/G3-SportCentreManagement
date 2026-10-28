"""QR-01 real Chromium fixture acceptance and decoding. Never calls a live financial API."""
import argparse
import base64
from datetime import datetime, timezone
from io import BytesIO
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
from urllib.parse import urlparse
from PIL import Image
import zxingcpp
from playwright.sync_api import sync_playwright, expect

parser=argparse.ArgumentParser()
parser.add_argument('--base',default='http://127.0.0.1:5195')
parser.add_argument('--normal-base',default='http://127.0.0.1:5196')
parser.add_argument('--before-base',default='http://127.0.0.1:5194')
parser.add_argument('--out',required=True)
args=parser.parse_args()
out=Path(args.out)
(out/'screenshots').mkdir(parents=True,exist_ok=True)
(out/'logs').mkdir(exist_ok=True)
sha=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
dirty=bool(subprocess.check_output(['git','status','--porcelain'],text=True).strip())
report={'sha':sha,'dirty':dirty,'mode':'FIXTURE/MOCK - intercepted local API only; no live financial operations',
        'checks':[],'screenshots':[],'requests':[],'blockedExternal':[],'comparisons':[],'decodes':[],'pageErrors':[]}
baseline='608a0f9ac1cd83beaaf5c46378ac66016e0c06bb'
cached='608a0f9ac1cd83beaaf5c46378ac66016e0c06bb'
tree=lambda commit:subprocess.check_output(['git','rev-parse',commit+':frontend'],text=True).strip()
report['baseline']={'sha':baseline,'cachedBuildSource':cached,'identicalFrontendTree':tree(baseline)==tree(cached)}
assert report['baseline']['identicalFrontendTree'], 'Before server must represent the approved source tree'
PAYMENT={'id':52,'code':'PAY-0052','status':'PENDING','amount':810000,'method':'MOMO','originalPrice':900000,
         'discount':90000,'itemDescription':'Demo football package','paymentTime':None,'memberId':2,'memberCode':'DEMO-MEMBER',
         'createdAt':'2026-10-28T09:00:00','bankTransferInstructions':{'bank':'PRIVATE BANK MUST NOT BE USED','accountName':'PRIVATE RECIPIENT','accountNumber':'PRIVATE-ACCOUNT','transferContent':'PRIVATE TRANSFER'}}
MEMBER={'id':2,'email':'member@example.invalid','fullName':'Demo Member','phone':None,'role':'MEMBER','status':'ACTIVE'}
PAYLOAD='SPORTIFY_DEMO_QR|VERSION=1|PAYMENT_ID=52|MODE=NON_PAYABLE|BANK_LABEL=VIETCOMBANK_DEMO'


def save(): (out/'logs/browser-results.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
def check(name,value,detail=None):
    report['checks'].append({'name':name,'pass':bool(value),'detail':detail});save()
    if not value:raise AssertionError(name+': '+str(detail))
def install(ctx,theme,mode='default',role='MEMBER'):
    storage={'sportify.themePreference':theme}
    if role!='ANONYMOUS':storage.update({'sportify.accessToken':'qr01-fixture-access','sportify.refreshToken':'fixture-refresh','sportify.sessionId':'qr01-fixture'})
    ctx.add_init_script(f'for(const [k,v] of Object.entries({json.dumps(storage)}))if(localStorage.getItem(k)===null)localStorage.setItem(k,v)')
    def route(r):
        request=r.request;url=urlparse(request.url)
        if '/api/v1/' in url.path:
            path=url.path.split('/api/v1')[-1]
            report['requests'].append({'method':request.method,'path':path,'mode':mode})
            if request.method not in ['GET','OPTIONS']:
                r.abort('blockedbyclient');check('No financial request during QR demo',False,path);return
            data=[];status=200
            if path=='/users/me':data=dict(MEMBER,role=role)
            elif path.startswith('/payments/') and path not in ['/payments/my']:
                if mode=='loading':return
                if mode in ['forbidden','error','missing']:status={'forbidden':403,'error':503,'missing':404}[mode];data={'message':'Synthetic payment unavailable'}
                else:data=dict(PAYMENT,id=53 if mode=='mismatch' else 52,status='SUCCESS' if mode=='terminal' else 'PENDING',method='BANK_TRANSFER' if mode=='bank-transfer' else 'MOMO')
            elif path=='/sports':data=[{'id':1,'code':'FOOTBALL','name':'Football'}]
            elif path=='/coaches/public':data=[{'id':22,'fullName':'Demo Coach','sportIds':[1]}]
            r.fulfill(status=status,json=data,content_type='application/json',headers={
                'Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'Authorization, Content-Type','Access-Control-Allow-Methods':'GET, OPTIONS'});return
        if url.hostname in ['localhost','127.0.0.1','::1']:r.continue_()
        else:report['blockedExternal'].append({'host':url.hostname or '', 'path':url.path});r.abort('blockedbyclient')
    ctx.route('**/*',route)


def page_for(browser,width,theme,route,mode='default',role='MEMBER',base=None):
    ctx=browser.new_context(viewport={'width':width,'height':900 if width==1440 else 1024 if width==768 else 844},reduced_motion='reduce')
    install(ctx,theme,mode,role)
    page=ctx.new_page();page.on('pageerror',lambda e:report['pageErrors'].append(str(e)))
    page.goto((base or args.base)+route)
    page.evaluate('document.fonts.ready')
    return ctx,page


def capture(page,theme,state,phase='after',full=True,pixels=None):
    width=page.viewport_size['width']
    size='desktop-1440' if width==1440 else 'tablet-768' if width==768 else 'mobile-390'
    file=out/f'screenshots/QR-01-i-round-01-{phase}-{theme}-{size}-{state}.png'
    if pixels is None:page.screenshot(path=str(file),full_page=full,animations='disabled')
    else:file.write_bytes(pixels)
    report['screenshots'].append({'path':str(file),'sha':baseline if phase=='before' else sha,'dirty':False if phase=='before' else dirty,'phase':phase,'route':urlparse(page.url).path,
        'theme':theme,'viewport':page.viewport_size,'state':state,'mode':report['mode'],'browser':report['browser'],
        'time':datetime.now(timezone.utc).isoformat(),'sha256':hashlib.sha256(file.read_bytes()).hexdigest()})
    save()


def styles(locator):
    return locator.evaluate('(e)=>{const s=getComputedStyle(e);return Object.fromEntries(["width","height","gap","font-family","font-size","font-style","font-weight","letter-spacing","border-radius","color","background-color"].map(k=>[k,s.getPropertyValue(k)]))}')


def decode(page,theme,width,pixels=None):
    symbol=page.get_by_role('img',name='Non-payable demo QR for payment 52',exact=False)
    if pixels is None:pixels=symbol.screenshot(animations='disabled')
    decoded=zxingcpp.read_barcode(Image.open(BytesIO(pixels)))
    check('Generic scanner decodes exact non-payable text '+theme+' '+str(width),decoded is not None and decoded.text==PAYLOAD)
    report['decodes'].append({'theme':theme,'width':width,'decodedText':decoded.text,'imageSha256':hashlib.sha256(pixels).hexdigest()})
    return symbol


with sync_playwright() as pw:
    browser=pw.chromium.launch(channel='msedge',headless=True)
    report['browser']='Microsoft Edge '+browser.version
    try:
        for width in [1440,768,390]:
            for theme in ['light','dark']:
                ctx,page=page_for(browser,width,theme,'/',role='ANONYMOUS')
                home={s:styles(page.locator('.home-header '+s)) for s in ['.brand__mark','.brand__name','.home-theme']}
                ctx.close()
                ctx,page=page_for(browser,width,theme,'/member/payments/52',base=args.before_base)
                expect(page.get_by_role('heading',name='Order summary',exact=True)).to_be_visible()
                check('Approved before has no QR demo entry',page.get_by_role('link',name='QR Payment (Demo)',exact=True).count()==0)
                capture(page,theme,'payment-entry-fixture',phase='before');ctx.close()
                ctx,page=page_for(browser,width,theme,'/member/payments/52')
                link=page.get_by_role('link',name='QR Payment (Demo)',exact=True)
                expect(link).to_be_visible()
                expect(page.get_by_role('button',name='Continue to payment',exact=True)).to_be_enabled()
                expect(page.get_by_role('radio',name='MoMo',exact=True)).to_be_disabled()
                for selector,expected in home.items():
                    actual=styles(page.locator('.payment-workspace-header '+selector))
                    check('Home parity '+selector+' '+theme+' '+str(width),actual==expected,{'actual':actual,'expected':expected})
                    report['comparisons'].append({'selector':selector,'theme':theme,'width':width,'actual':actual,'expected':expected})
                capture(page,theme,'payment-entry-fixture')
                link.focus();page.keyboard.press('Tab');page.keyboard.press('Shift+Tab')
                check('Entry keyboard focus visible',link.evaluate('(e)=>getComputedStyle(e).outlineStyle')!='none')
                page.keyboard.press('Enter')
                symbol=decode(page,theme,width)
                expect(page.get_by_text('810.000 VND',exact=True)).to_be_visible()
                expect(page.get_by_text('DEMO ONLY - NO MONEY WILL BE TRANSFERRED',exact=True)).to_be_visible()
                expect(page.get_by_text('DEMO ONLY - NOT A REAL PAYMENT',exact=True)).to_be_visible()
                check('No actual bank instructions rendered',all(s not in page.locator('.qr-demo-card').inner_text() for s in ['PRIVATE RECIPIENT','PRIVATE-ACCOUNT','PRIVATE TRANSFER']))
                check('Only expected navigation action',page.locator('.qr-demo-card button,.qr-demo-card a').count()==0)
                check('No page overflow',page.evaluate('document.documentElement.scrollWidth<=innerWidth'))
                capture(page,theme,'demo-card-fixture')
                page.evaluate('window.scrollTo(0,0)')
                page.get_by_role('button',name='Switch to '+('dark' if theme=='light' else 'light')+' mode',exact=True).click()
                page.reload();expect(page.locator('html')).to_have_attribute('data-theme','dark' if theme=='light' else 'light')
                check('Theme persists on demo reload',True)
                page.get_by_role('link',name='Back to Payment Summary',exact=True).click()
                expect(page.get_by_role('heading',name='Order summary',exact=True)).to_be_visible()
                ctx.close()
        for mode in ['loading','error','missing','forbidden','mismatch','terminal']:
            ctx,page=page_for(browser,390,'dark','/member/payments/52/qr-demo',mode=mode)
            if mode=='loading':expect(page.get_by_text('Loading the payment demo',exact=True)).to_be_attached()
            elif mode in ['error','missing','forbidden']:expect(page.get_by_role('heading',name='We could not load the payment demo',exact=True)).to_be_visible()
            else:expect(page.get_by_role('heading',name='Payment details unavailable' if mode=='mismatch' else 'Demo unavailable for this payment',exact=True)).to_be_visible()
            check(mode+' does not render a QR',page.locator('.qr-demo-card__symbol').count()==0)
            capture(page,'dark',mode+'-fixture');ctx.unroute_all(behavior='ignoreErrors');ctx.close()
        for route in ['/member/payments/invalid/qr-demo','/member/payments/9007199254740992/qr-demo']:
            ctx,page=page_for(browser,390,'light',route)
            expect(page.get_by_text('The demo payment link is not valid.',exact=True)).to_be_visible()
            capture(page,'light','invalid-link-'+('unsafe-integer' if '9007' in route else 'text')+'-fixture');ctx.close()
        for role in ['ANONYMOUS','RECEPTIONIST']:
            start=len(report['requests']);ctx,page=page_for(browser,390,'light','/member/payments/52/qr-demo',role=role)
            expect(page.get_by_role('heading',level=1)).to_be_visible()
            check(role+' does not read a payment',not any(r['path'].startswith('/payments/') for r in report['requests'][start:]))
            check(role+' cannot view QR',page.locator('.qr-demo-card__symbol').count()==0)
            capture(page,'light',role.lower()+'-guard-fixture');ctx.close()
        ctx,page=page_for(browser,390,'light','/member/payments/52/status',mode='bank-transfer')
        expect(page.get_by_role('heading',name='Payment Instructions and Status',exact=False)).to_be_visible()
        expect(page.get_by_role('heading',name='Bank transfer',exact=False)).to_be_visible()
        capture(page,'light','existing-ui-28-fixture');ctx.close()
        start=len(report['requests']);ctx,page=page_for(browser,390,'light','/member/payments/52/qr-demo',base=args.normal_base)
        expect(page.get_by_role('heading',name='Page not found',exact=True)).to_be_visible()
        check('Normal production refuses QR data fetch',not any(r['path'].startswith('/payments/') for r in report['requests'][start:]))
        capture(page,'light','normal-production-disabled-fixture');page.goto(args.normal_base+'/member/payments/52')
        expect(page.get_by_role('heading',name='Order summary',exact=True)).to_be_visible()
        check('Normal production entry absent even with build flag true',page.get_by_role('link',name='QR Payment (Demo)',exact=True).count()==0)
        ctx.close()
        for theme in ['light','dark']:
            with tempfile.TemporaryDirectory(prefix='qr01-native-zoom-',ignore_cleanup_errors=True) as profile:
                native=pw.chromium.launch_persistent_context(profile,channel='msedge',headless=True,viewport={'width':1440,'height':900},reduced_motion='reduce')
                settings=native.pages[0];settings.goto('edge://settings/appearance')
                zoom=settings.evaluate('()=>new Promise(r=>chrome.settingsPrivate.setDefaultZoom(2,()=>chrome.settingsPrivate.getDefaultZoom(r)))')
                install(native,theme);page=native.new_page();page.goto(args.base+'/member/payments/52/qr-demo')
                expect(page.locator('.qr-demo-card')).to_be_visible()
                metrics=page.evaluate('({width:innerWidth,height:innerHeight,dpr:devicePixelRatio,scale:visualViewport.scale,overflow:document.documentElement.scrollWidth>innerWidth})')
                check('Actual native zoom2 '+theme,zoom==2 and metrics=={'width':720,'height':450,'dpr':2,'scale':1,'overflow':False},metrics)
                page.locator('.qr-demo-card__symbol').scroll_into_view_if_needed()
                page.evaluate('()=>{const q=document.querySelector(".qr-demo-card__symbol").getBoundingClientRect(),h=document.querySelector(".payment-workspace-header").getBoundingClientRect();window.scrollBy(0,q.top-h.bottom-16)}')
                check('Native QR fits below sticky header '+theme,page.locator('.qr-demo-card__symbol').evaluate('(e)=>{const q=e.getBoundingClientRect(),h=document.querySelector(".payment-workspace-header").getBoundingClientRect();return q.top>=h.bottom&&q.bottom<=innerHeight}'))
                # Native browser zoom changes CDP layout coordinates. Capture the compositor surface
                # without a Playwright clip so the image is the actual physical viewport.
                cdp=native.new_cdp_session(page)
                surface=cdp.send('Page.captureScreenshot',{'format':'png','fromSurface':True,'captureBeyondViewport':False})
                pixels=base64.b64decode(surface['data'])
                capture(page,theme,'native-200pct-visible-qr-fixture',full=False,pixels=pixels)
                decode(page,theme,720,pixels=pixels)
                native.close()
        check('Every API request was read-only',all(r['method'] in ['GET','OPTIONS'] for r in report['requests']))
        check('No QR service/payment hostname requested',not any(any(s in r['host'] for s in ['vietqr','napas','bank','momo','vnpay','zalopay']) for r in report['blockedExternal']))
        check('No uncaught application errors',not report['pageErrors'],report['pageErrors'])
    finally:
        browser.close();save()
print(json.dumps({'sha':sha,'checks':len(report['checks']),'screenshots':len(report['screenshots']),'decodedQrs':len(report['decodes'])}))
