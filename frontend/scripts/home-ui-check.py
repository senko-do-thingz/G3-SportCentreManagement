"""Home interaction evidence. Use requirements-visual-check.txt and a Vite preview build.

python scripts/home-ui-check.py --base http://127.0.0.1:4173 --out <private-artifact-directory>
Uses real Playwright mouse, keyboard and touchscreen events. No production account is used.
"""
import argparse
import hashlib
import json
import re
import subprocess
import time
import traceback
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse

from playwright.sync_api import sync_playwright, expect

NAMES = ['Football', 'Badminton', 'Basketball', 'Volleyball', 'Swimming', 'Tennis']
SPORTS = [{'id': i + 1, 'code': name.upper(), 'name': name} for i, name in enumerate(NAMES)]
COACHES = [{'id': 22, 'fullName': 'Demo Coach', 'sportIds': [1], 'headline': 'Football coach', 'yearsExperience': 4}]
MEMBER = {'id': 2, 'email': 'browser@example.test', 'fullName': 'Browser Member', 'phone': None, 'role': 'MEMBER', 'status': 'ACTIVE'}


def contrast(a, b):
    def luminance(rgb):
        values = [float(v) / 255 for v in re.findall(r'[\d.]+', rgb)[:3]]
        linear = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in values]
        return sum(v * weight for v, weight in zip(linear, [.2126, .7152, .0722]))
    high, low = sorted([luminance(a), luminance(b)], reverse=True)
    return (high + .05) / (low + .05)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', default='http://127.0.0.1:4173')
    parser.add_argument('--out', required=True)
    parser.add_argument('--channel', default='msedge')
    parser.add_argument('--case', help='Development-only regex subset; omit for the full regression')
    parser.add_argument('--live-api', action='store_true', help='Also verify the existing backend without API interception')
    parser.add_argument('--live-origin', help='Optional allowed browser origin; bridge only frontend assets from --base, never API requests')
    args = parser.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    dirty = bool(subprocess.check_output(['git', 'status', '--porcelain'], text=True).strip())
    report = {'sha': sha, 'dirty': dirty, 'capturedAt': datetime.now(timezone.utc).isoformat(), 'cases': [], 'screenshots': [], 'contrast': [], 'selection': args.case, 'console': [], 'notRun': ['Native browser zoom 200%', 'Assistive technology speech output']}
    report['buildAssets'] = {str(p.relative_to(Path('dist'))): hashlib.sha256(p.read_bytes()).hexdigest()
        for p in [Path('dist/index.html'), *Path('dist/assets').glob('index-*.js'), *Path('dist/assets').glob('index-*.css')] if p.is_file()}

    with sync_playwright() as pw:
        browser = pw.chromium.launch(channel=args.channel, headless=True)
        classic_browser = pw.chromium.launch(channel=args.channel, headless=True, ignore_default_args=['--hide-scrollbars'])
        report['browser'] = f'{args.channel} {browser.version}'

        def page_for(width=1440, height=900, theme='light', growth=0, touch=False, member=False, media=False, fragment='', motion='reduce', api_mode='success', classic=False):
            context = (classic_browser if classic else browser).new_context(viewport={'width': width, 'height': height}, reduced_motion='no-preference' if media else motion, has_touch=touch, is_mobile=touch)
            seed = {'sportify.themePreference': theme}
            if member:
                seed.update({'sportify.accessToken': 'home-ui-fixture', 'sportify.refreshToken': 'fixture-refresh', 'sportify.sessionId': 'home-ui-fixture'})
            context.add_init_script(f'for (const [key, value] of Object.entries({json.dumps(seed)})) if (localStorage.getItem(key) === null) localStorage.setItem(key, value)')
            sports = SPORTS + [{'id': 7 + i, 'code': f'FUTURE_{i}', 'name': f'Future sport {i + 1}'} for i in range(growth)]

            sports_calls = 0
            def api(route):
                nonlocal sports_calls
                path = urlparse(route.request.url).path.split('/api/v1')[-1]
                body = {
                    '/sports': sports, '/coaches/public': COACHES, '/packages': [],
                    '/membership-cards/tiers': [], '/facilities/public': [], '/users/me': MEMBER,
                    '/sessions': {'content': [], 'page': 0, 'size': 20, 'totalElements': 0, 'totalPages': 0},
                }.get(path, [])
                if api_mode == 'empty': body = []
                if path == '/sports':
                    sports_calls += 1
                    if api_mode == 'retry' and sports_calls == 1:
                        route.fulfill(status=503, json={'message': 'Synthetic unavailable fixture'})
                        return
                route.fulfill(status=200, json=body, headers={'Access-Control-Allow-Origin': '*'})
            context.route('**/api/v1/**', api)
            context.route('https://images.unsplash.com/**', lambda route: route.abort())
            if not media:
                context.route('https://assets.mixkit.co/**', lambda route: route.abort())
            page = context.new_page()
            page.on('console', lambda message: report['console'].append({'type': message.type, 'text': message.text, 'url': page.url}) if message.type in ['warning', 'error'] else None)
            page.on('pageerror', lambda error: report['console'].append({'type':'uncaught', 'text':str(error), 'url':page.url}))
            page.set_default_timeout(7000)
            page.goto(args.base.rstrip('/') + '/' + fragment, wait_until='load')
            if classic:
                page.add_style_tag(content='::-webkit-scrollbar { width: 17px; height: 17px; }')
            expect(page.get_by_role('button', name='Sport All sports')).to_be_visible()
            if api_mode != 'empty': expect(page.get_by_role('button', name='View profile of Demo Coach')).to_have_count(1)
            page.evaluate('document.fonts.ready')
            return context, page

        def case(name, fn):
            if args.case and not re.search(args.case, name): return
            start = time.monotonic()
            console_start = len(report['console'])
            try:
                details = fn()
                assert not any(item['type']=='uncaught' for item in report['console'][console_start:]), report['console'][console_start:]
                report['cases'].append({'name': name, 'status': 'PASS', 'details': details, 'seconds': round(time.monotonic() - start, 2)})
                print(f'PASS {name}', flush=True)
            except Exception as error:
                report['cases'].append({'name': name, 'status': 'FAIL', 'error': str(error), 'traceback': traceback.format_exc()})
                print(f'FAIL {name}: {error}', flush=True)
                contexts = [*browser.contexts, *classic_browser.contexts]
                if contexts and contexts[-1].pages:
                    contexts[-1].pages[-1].screenshot(path=str(out / f'FAIL-{name}.png'))
            finally:
                for context in [*browser.contexts, *classic_browser.contexts]:
                    context.close()

        def snapshot(page, name, theme, width):
            file = f'{theme}-{width}-{name}.png'
            page.screenshot(path=str(out / file), animations='disabled')
            url = urlparse(page.url)
            route = url.path + (f'#{url.fragment}' if url.fragment else '')
            report['screenshots'].append({'file': file, 'route': route, 'theme': theme, 'viewport': page.viewport_size, 'scrollY': page.evaluate('scrollY'), 'sha': sha, 'dirty': dirty})

        def header_matrix(width, theme):
            _, page = page_for(width=width, theme=theme)
            header = page.locator('.home-header')
            for name, y in [('top', 0), ('viewport', 900), ('bottom', 100000)]:
                page.evaluate('(y) => scrollTo(0,y)', y)
                page.wait_for_timeout(180)
                rect = header.bounding_box()
                assert abs(rect['y']) < 1 and rect['height'] < 220, rect
                assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'Horizontal overflow'
                if y:
                    expect(header).to_have_class(re.compile('home-header--solid'))
                else:
                    assert header.evaluate('(e) => getComputedStyle(e).backgroundColor') == 'rgba(0, 0, 0, 0)'
                snapshot(page, name, theme, width)
            if width <= 1100:
                page.get_by_role('button', name='Menu', exact=True).click()
            page.get_by_role('navigation', name='Home navigation').get_by_role('link', name='Sports', exact=True).click()
            page.wait_for_timeout(100)
            assert page.locator('#sports').bounding_box()['y'] >= header.bounding_box()['height']
            snapshot(page, 'anchor', theme, width)
            return 'Fixed header at 0, one viewport and bottom; section heading visible; no horizontal overflow'

        for theme in ['light', 'dark']:
            for width in [320, 390, 768, 1440]:
                case(f'header-{theme}-{width}', lambda w=width, t=theme: header_matrix(w, t))

        def nav_interactions():
            _, page = page_for()
            nav = page.get_by_role('navigation', name='Home navigation')
            parent = nav.get_by_role('link', name='Sports', exact=True)
            toggle = nav.get_by_role('button', name='Toggle Sports destinations', exact=True)
            parent.hover()
            expect(toggle).to_have_attribute('aria-expanded', 'true')
            child = nav.get_by_role('link', name='Football', exact=True)
            box = child.bounding_box()
            page.mouse.move(box['x'] + box['width'] / 2, box['y'] + box['height'] / 2, steps=12)
            expect(child).to_be_visible()
            snapshot(page, 'hover-submenu', 'light', 1440)
            page.mouse.move(1300, 600, steps=12)
            expect(toggle).to_have_attribute('aria-expanded', 'false')
            # Start at the document and use browser-native Tab traversal, including skip link and brand.
            page.reload(wait_until='load')
            for _ in range(8):
                if parent.evaluate('(e) => e === document.activeElement'):
                    break
                page.keyboard.press('Tab')
            expect(parent).to_be_focused()
            expect(toggle).to_have_attribute('aria-expanded', 'true')
            assert parent.inner_text() == 'Sports' and not re.search(r'[+-]', toggle.inner_text())
            page.keyboard.press('Tab')
            expect(toggle).to_be_focused()
            page.keyboard.press('Space')
            expect(toggle).to_have_attribute('aria-expanded', 'false')
            page.keyboard.press('Enter')
            page.keyboard.press('Tab')
            expect(child).to_be_focused()
            page.keyboard.press('Escape')
            expect(toggle).to_be_focused()
            expect(toggle).to_have_attribute('aria-expanded', 'false')
            assert toggle.evaluate('(e) => getComputedStyle(e).outlineStyle') != 'none'
            snapshot(page, 'keyboard-focus', 'light', 1440)
            page.keyboard.press('Enter')
            page.get_by_role('heading', name=re.compile('Find your sport')).click()
            expect(toggle).to_have_attribute('aria-expanded', 'false')
            assert nav.get_by_role('button', name=re.compile('Home destinations')).count() == 0
            return 'Mouse corridor/exit/outside click, native Tab/Space/Enter/Escape, visible focus, Home leaf'
        case('desktop-native-navigation', nav_interactions)

        def child_destinations():
            _, page = page_for()
            nav = page.get_by_role('navigation', name='Home navigation')
            count = 0
            for name in ['Sports', 'Packages', 'FAQ']:
                parent = nav.get_by_role('link', name=name, exact=True)
                parent.hover()
                links = nav.get_by_role('list', name=f'{name} destinations').get_by_role('link')
                targets = [(link.inner_text(), link.get_attribute('href')) for link in links.all()]
                for label, href in targets:
                    parent.hover()
                    nav.get_by_role('list', name=f'{name} destinations').get_by_role('link', name=label, exact=True).click()
                    expect(page).to_have_url(re.compile(re.escape(href) + '$'))
                    target = page.locator(href)
                    if href.startswith('#home-faq-'):
                        expect(target.locator('p')).to_be_visible()
                    assert target.bounding_box()['y'] >= page.locator('.home-header').bounding_box()['height'] - 1, href
                    expect(nav.get_by_role('button', name=f'Toggle {name} destinations', exact=True)).to_have_attribute('aria-expanded', 'false')
                    count += 1
            return f'{count} real content destinations navigated'
        case('all-child-destinations', child_destinations)

        def touch_navigation():
            _, page = page_for(width=390, height=844, touch=True)
            page.get_by_role('button', name='Menu', exact=True).tap()
            toggle = page.get_by_role('button', name='Toggle Sports destinations', exact=True)
            toggle.tap()
            expect(toggle).to_have_attribute('aria-expanded', 'true')
            snapshot(page, 'touch-submenu', 'light', 390)
            toggle.tap()
            expect(toggle).to_have_attribute('aria-expanded', 'false')
            toggle.tap()
            page.get_by_role('navigation', name='Home navigation').get_by_role('link', name='Tennis', exact=True).tap()
            expect(page).to_have_url(re.compile('#home-sport-tennis$'))
            expect(page.get_by_role('button', name='Menu', exact=True)).to_have_attribute('aria-expanded', 'false')
        case('touch-navigation', touch_navigation)

        def faq_visible(page, index):
            item = page.locator(f'#home-faq-{index}')
            answer = item.locator('p')
            expect(answer).to_be_visible()
            expect(item.locator('summary')).to_be_focused()
            rect = answer.bounding_box()
            assert rect['y'] >= page.locator('.home-header').bounding_box()['height'] - 1, rect
            assert rect['y'] + rect['height'] <= page.viewport_size['height'] + 1, rect

        def faq_navigation(mode, width):
            _, page = page_for(width=width, height=844, touch=mode == 'touch')
            nav = page.get_by_role('navigation', name='Home navigation')
            item = page.locator('#home-faq-2')
            question = item.locator('summary').inner_text()
            expect(item.locator('p')).not_to_be_visible()
            for attempt in range(2):
                if mode == 'touch':
                    page.get_by_role('button', name='Menu', exact=True).tap()
                    nav.get_by_role('button', name='Toggle FAQ destinations', exact=True).tap()
                    nav.get_by_role('link', name=question, exact=True).tap()
                elif mode == 'keyboard':
                    parent = nav.get_by_role('link', name='FAQ', exact=True)
                    for _ in range(100):
                        if parent.evaluate('(e) => e === document.activeElement'):
                            break
                        page.keyboard.press('Tab')
                    expect(parent).to_be_focused()
                    for _ in range(4):
                        page.keyboard.press('Tab')
                    expect(nav.get_by_role('link', name=question, exact=True)).to_be_focused()
                    page.keyboard.press('Enter')
                else:
                    nav.get_by_role('link', name='FAQ', exact=True).hover()
                    nav.get_by_role('link', name=question, exact=True).click()
                expect(page).to_have_url(re.compile('#home-faq-2$'))
                faq_visible(page, 2)
                expect(page.locator('#home-faq-3 p')).not_to_be_visible()
                snapshot(page, f'faq-{mode}-reveal-{attempt}', 'light', width)
                if mode == 'keyboard':
                    page.keyboard.press('Space')
                elif mode == 'touch':
                    item.locator('summary').tap()
                else:
                    item.locator('summary').click()
                expect(item.locator('p')).not_to_be_visible()
            return 'FAQ 2 answer visible, summary focused, native close retained, same hash reopens on repeat'
        for mode, width in [('mouse', 1440), ('keyboard', 1440), ('touch', 390), ('touch', 320)]:
            case(f'faq-{mode}-{width}', lambda m=mode, w=width: faq_navigation(m, w))

        def faq_hash(width):
            _, page = page_for(width=width, fragment='#home-faq-3')
            faq_visible(page, 3)
            assert page.locator('.home-faq__list details[open]').count() == 2
            page.locator('#home-faq-3 summary').click()
            expect(page.locator('#home-faq-3 p')).not_to_be_visible()
            page.evaluate('location.hash = "#home-faq-2"')
            faq_visible(page, 2)
            page.evaluate('location.hash = "#home-faq-3"')
            faq_visible(page, 3)
            expect(page.locator('#home-faq-1 p')).not_to_be_visible()
            page.reload(wait_until='load')
            faq_visible(page, 3)
            snapshot(page, 'faq-direct-hash', 'light', width)
            return 'Direct FAQ 3 load, native hash changes, reload and independent disclosure state'
        for width in [320, 390, 1440]:
            case(f'faq-direct-hash-{width}', lambda w=width: faq_hash(w))

        def list_directions(width, count, theme):
            _, page = page_for(width=width, height=640 if width == 320 else 844 if width == 390 else 900, growth=count - 7, theme=theme)
            page.evaluate('window.homeUiSubmits=0; document.querySelector(".home-finder form").addEventListener("submit",()=>window.homeUiSubmits++)')
            trigger = page.get_by_role('button', name=re.compile('^Sport '))
            trigger.click()
            page.wait_for_timeout(300)
            menu = page.get_by_role('listbox', name='Sport', exact=True)
            options = menu.get_by_role('option')
            expect(options).to_have_count(count)
            trace=[]
            def active(index, key):
                option=options.nth(index)
                expect(menu).to_have_attribute('aria-activedescendant', option.get_attribute('id'))
                expect(menu).to_be_focused()
                box=option.bounding_box()
                trace.append({'key':key,'index':index,'box':box,'scrollTop':menu.evaluate('(e)=>e.scrollTop')})
                return box
            start=active(0,'open')
            page.keyboard.press('ArrowDown')
            down=active(1,'ArrowDown')
            assert down['y'] > start['y'] and abs(down['x']-start['x']) < 1
            for key in ['ArrowRight','ArrowLeft']:
                page.keyboard.press(key); active(1,key+' ignored')
            page.keyboard.press('ArrowUp'); active(0,'ArrowUp')
            page.keyboard.press('ArrowUp'); active(0,'top boundary')
            boxes=[o.bounding_box() for o in options.all()]
            assert all(abs(b['x']-boxes[0]['x'])<1 for b in boxes)
            assert all(boxes[i+1]['y']>boxes[i]['y'] for i in range(count-1))
            assert abs(menu.bounding_box()['width']-trigger.bounding_box()['width'])<2
            assert menu.bounding_box()['y'] >= trigger.bounding_box()['y']+trigger.bounding_box()['height']+1
            assert menu.evaluate('(e)=>getComputedStyle(e).position')=='absolute'
            snapshot(page,f'list-{count}',theme,width)
            page.keyboard.press('End'); last=active(count-1,'End')
            rect=menu.bounding_box()
            assert last['y']>=rect['y'] and last['y']+last['height']<=rect['y']+rect['height']+1
            assert options.last.evaluate('(e)=>{const r=e.getBoundingClientRect(); return document.elementFromPoint(r.right-8,r.top+r.height/2)?.closest("[role=option]")===e}'), 'Last option hit target is obstructed'
            expect(page.get_by_role('button',name='Back to top',include_hidden=True)).not_to_be_visible()
            for key in ['ArrowDown','ArrowLeft','ArrowRight']:
                page.keyboard.press(key); active(count-1,key+' at end')
            snapshot(page,f'list-{count}-last',theme,width)
            page.keyboard.press('ArrowUp'); active(count-2,'ArrowUp from end')
            expect(options.first).to_have_attribute('aria-selected','true')
            expect(trigger).to_have_text('All sports')
            page.keyboard.press('Home'); active(0,'Home')
            page.keyboard.press('ArrowDown'); page.keyboard.press('Enter')
            expect(trigger).to_have_text('Football'); expect(trigger).to_be_focused()
            if page.evaluate('scrollY')>600: expect(page.get_by_role('button',name='Back to top')).to_be_visible()
            trigger.click(); expect(page.get_by_role('option',name='Football',exact=True)).to_have_attribute('aria-selected','true')
            page.keyboard.press('Escape'); expect(trigger).to_be_focused()
            trigger.click(); page.keyboard.press('Tab')
            expect(page.get_by_role('button',name='Level All levels')).to_be_focused()
            assert page.get_by_role('listbox').count()==0 and page.evaluate('window.homeUiSubmits')==0
            return {'count':count,'theme':theme,'trace':trace,'submitted':0}
        for theme in ['light','dark']:
            for width in [320,390,768,1440]:
                for count in [7,31]:
                    case(f'list-directions-{theme}-{width}-{count}',lambda w=width,c=count,t=theme:list_directions(w,c,t))

        def filters(width=1440, height=900, growth=0):
            _, page = page_for(width=width, height=height, growth=growth)
            page.locator('#finder').evaluate('(e) => e.scrollIntoView({block:"center"})')
            trigger = page.get_by_role('button', name=re.compile('^Sport '))
            trigger.click()
            menu = page.get_by_role('listbox', name='Sport', exact=True)
            expect(menu).to_be_visible()
            expect(menu.get_by_role('option')).to_have_count(7 + growth)
            geometry = menu.evaluate('(e) => ({scroll:e.scrollHeight, height:e.clientHeight, rect:e.getBoundingClientRect().toJSON(), placement:e.dataset.placement})')
            assert geometry['rect']['y'] >= page.locator('.home-header').bounding_box()['height']
            assert geometry['rect']['bottom'] <= height + 1
            if width == 1440 and height == 900 and not growth:
                assert geometry['scroll'] <= geometry['height'] + 1, geometry
            trigger_box=trigger.bounding_box()
            assert geometry['placement']=='below'
            assert geometry['rect']['y'] >= trigger_box['y']+trigger_box['height']
            snapshot(page, f'filter-{height}-growth-{growth}', 'light', width)
            page.keyboard.press('End')
            last = menu.get_by_role('option').last
            box = last.bounding_box()
            assert box['y'] >= geometry['rect']['y'] and box['y'] + box['height'] <= geometry['rect']['bottom'] + 1
            page.keyboard.press('Enter')
            expect(trigger).to_be_focused()
            trigger.click()
            page.keyboard.press('Escape')
            expect(trigger).to_be_focused()
            trigger.click()
            page.keyboard.press('Tab')
            expect(page.get_by_role('button', name=re.compile('^Level '))).to_be_focused()
            assert menu.count() == 0
            return geometry
        for width, height, growth in [(1440, 900, 0), (768, 900, 0), (390, 540, 0), (1440, 480, 0), (1440, 900, 24), (320, 640, 24)]:
            case(f'filter-{width}-{height}-growth-{growth}', lambda w=width, h=height, g=growth: filters(w, h, g))

        def below_placement(width, height, motion):
            _,page=page_for(width=width,height=height,motion=motion)
            trigger=page.get_by_role('button',name=re.compile('^Sport '))
            trigger.evaluate('(e)=>scrollBy(0,e.getBoundingClientRect().bottom-(innerHeight-32))')
            before=page.evaluate('scrollY')
            trigger.click()
            page.wait_for_timeout(800)
            menu=page.get_by_role('listbox',name='Sport',exact=True)
            rect=menu.bounding_box(); anchor=trigger.bounding_box(); after=page.evaluate('scrollY')
            assert rect['y']>=anchor['y']+anchor['height'] and rect['y']+rect['height']<=height+1
            assert 0 < after-before <= min(360,height*.4)+2
            expect(menu).to_have_attribute('data-placement','below')
            page.evaluate('scrollBy(0,32)'); page.wait_for_timeout(120)
            assert abs(menu.bounding_box()['y']-trigger.bounding_box()['y']-trigger.bounding_box()['height']-8)<2
            page.keyboard.press('End'); page.keyboard.press('Enter')
            expect(trigger).to_have_text('Tennis'); expect(trigger).to_be_focused()
            trigger.click(); page.keyboard.press('Escape'); expect(trigger).to_be_focused()
            trigger.click(); page.wait_for_timeout(250)
            snapshot(page,f'below-near-bottom-{height}-{motion}','light',width)
            return {'scrollAssist':after-before,'menu':rect,'trigger':anchor,'motion':motion}
        for width,height in [(320,640),(390,844),(768,900),(1440,900),(1440,480)]:
            for motion in ['reduce','no-preference']:
                case(f'below-near-bottom-{width}-{height}-{motion}',lambda w=width,h=height,m=motion:below_placement(w,h,m))

        def selections():
            _, page = page_for(member=True)
            for i, sport in enumerate(NAMES):
                page.goto(args.base, wait_until='load')
                trigger = page.get_by_role('button', name=re.compile('^Sport '))
                trigger.click()
                page.get_by_role('option', name=sport, exact=True).click()
                expect(trigger).to_have_text(sport)
                page.get_by_role('button', name='Find classes', exact=True).click()
                expect(page).to_have_url(re.compile(f'/member/classes\\?sportId={i + 1}$'))
            page.goto(args.base, wait_until='load')
            trigger = page.get_by_role('button', name=re.compile('^Sport '))
            trigger.click()
            page.get_by_role('option', name='Football', exact=True).click()
            trigger.click()
            expect(page.get_by_role('option', name='Football', exact=True)).to_have_attribute('aria-selected', 'true')
            page.get_by_role('option', name='All sports', exact=True).click()
            page.get_by_role('button', name='Level All levels').click()
            page.get_by_role('option', name='Beginner', exact=True).click()
            page.get_by_role('button', name='Training Any format').click()
            page.get_by_role('option', name='Coach-led', exact=True).click()
            page.get_by_role('button', name='Find classes', exact=True).click()
            expect(page).to_have_url(re.compile('/member/classes\\?level=BEGINNER&format=COACH_LED$'))
            return 'Six sports preserve query routing; reset and independent Level/Training query verified'
        case('selection-and-search-routing', selections)

        def identity():
            _, page = page_for()
            def logo():
                # Wait for the Home mount; the auth page also has a shared Brand during navigation.
                return page.locator('.home-header .brand').evaluate('(e) => {const read=(selector,keys)=>{const s=getComputedStyle(e.querySelector(selector));return Object.fromEntries(keys.map(k=>[k,s[k]]))};return {tile:read(".brand__mark",["backgroundColor","color","borderTopWidth","borderRadius","boxShadow","filter","width","height"]),wordmark:read(".brand__name",["color","fontFamily","fontStyle","fontSize","letterSpacing","filter"])}}')
            light = logo()
            for theme in ['dark', 'light']:
                page.get_by_role('button', name=f'Switch to {theme} mode').click()
                assert logo() == light
                colors = page.locator('.home-page').evaluate('(e) => {const selectors=[".home-strip__offer",".home-finder .btn--primary",".home-account .btn--primary",".home-hero .home-btn--primary",".home-cta .home-btn--primary"]; return selectors.map(x=>{const s=getComputedStyle(e.querySelector(x));return {selector:x,bg:s.backgroundColor,fg:s.color}})}')
                for item in colors:
                    assert item['bg'] == 'rgb(31, 79, 216)', item
                    ratio = contrast(item['fg'], item['bg'])
                    assert ratio >= 4.5, item
                    report['contrast'].append({'theme': theme, **item, 'ratio': ratio})
                page.get_by_role('navigation', name='Home navigation').get_by_role('link', name='Sports', exact=True).hover()
                submenu = page.get_by_role('list', name='Sports destinations')
                fg, bg = submenu.evaluate('(e) => [getComputedStyle(e.querySelector("a")).color,getComputedStyle(e).backgroundColor]')
                assert contrast(fg, bg) >= 4.5
                report['contrast'].append({'theme': theme, 'selector': 'Home submenu', 'fg': fg, 'bg': bg, 'ratio': contrast(fg, bg)})
                snapshot(page, 'theme-submenu', theme, 1440)
                page.mouse.move(1300, 600)
                trigger = page.get_by_role('button', name='Sport All sports')
                trigger.click()
                menu = page.get_by_role('listbox', name='Sport', exact=True)
                fg, bg = menu.evaluate('(e) => [getComputedStyle(e.querySelectorAll("li")[1]).color,getComputedStyle(e).backgroundColor]')
                assert contrast(fg, bg) >= 4.5
                report['contrast'].append({'theme': theme, 'selector': 'Sport option', 'fg': fg, 'bg': bg, 'ratio': contrast(fg, bg)})
                page.keyboard.press('Escape')
            page.get_by_role('button', name='Switch to dark mode').click()
            page.get_by_role('link', name='Photo credits').click()
            expect(page).to_have_url(re.compile('/credits$'))
            page.get_by_role('link', name='Back to Home').first.click()
            assert logo() == light
            for route in ['/login', '/register']:
                page.goto(args.base + route, wait_until='load')
                page.get_by_role('link', name='Sportify Sports center home').click()
                assert logo() == light
                expect(page.locator('html')).to_have_attribute('data-theme', 'dark')
            return light
        case('logo-theme-routes-and-cta-contrast', identity)

        def independence():
            _, page = page_for()
            page.get_by_role('button', name='Sport All sports').click()
            page.get_by_role('navigation', name='Home navigation').get_by_role('link', name='Sports', exact=True).hover()
            expect(page.get_by_role('listbox', name='Sport', exact=True)).to_be_visible()
            expect(page.get_by_role('list', name='Sports destinations', exact=True)).to_be_visible()
            page.mouse.click(1200, 600)
            expect(page.get_by_role('listbox')).to_have_count(0)
        case('nav-and-filter-independence', independence)

        def reduced_motion():
            _, page = page_for(width=320, height=640)
            assert page.locator('video').count() == 0
            page.get_by_role('button', name='Menu', exact=True).click()
            page.get_by_role('button', name='Toggle FAQ destinations', exact=True).click()
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
            snapshot(page, 'reflow-menu', 'light', 320)
        case('reduced-motion-and-320-reflow', reduced_motion)

        def authenticated_header():
            _, page = page_for(member=True)
            expect(page.get_by_role('link', name='My workspace')).to_have_attribute('href', '/member')
            expect(page.get_by_role('button', name='Log out')).to_be_visible()
            expect(page.get_by_role('navigation', name='Home navigation').get_by_role('link', name='Sports', exact=True)).to_be_visible()
        case('authenticated-public-navigation', authenticated_header)

        def smooth_navigation(motion):
            _,page=page_for(motion=motion)
            page.evaluate('''() => {window.homeScrollCalls=[];
                const original=Element.prototype.scrollIntoView;
                Element.prototype.scrollIntoView=function(options){window.homeScrollCalls.push({id:this.id,...options}); return original.call(this,options)} }''')
            nav=page.get_by_role('navigation',name='Home navigation')
            initial=page.evaluate('history.length')
            nav.get_by_role('link',name='Sports',exact=True).hover()
            nav.get_by_role('link',name='Sports',exact=True).click()
            page.wait_for_timeout(30)
            early=page.evaluate('scrollY')
            page.wait_for_function('Math.abs(document.querySelector("#sports").getBoundingClientRect().top-document.querySelector(".home-header").getBoundingClientRect().height-20)<2')
            final=page.evaluate('scrollY')
            assert final>100
            if motion=='reduce': assert abs(early-final)<1
            else: assert early<final-2, (early,final)
            calls=page.evaluate('window.homeScrollCalls')
            assert len(calls)==1 and calls[0]['behavior']==('instant' if motion=='reduce' else 'smooth'),calls
            assert page.evaluate('history.length')==initial+1
            nav.get_by_role('link',name='Sports',exact=True).hover()
            nav.get_by_role('link',name='Sports',exact=True).click()
            assert page.evaluate('history.length')==initial+1
            assert len(page.evaluate('window.homeScrollCalls'))==2
            page.go_back(); expect(page).to_have_url(re.compile('/$'))
            page.go_forward(); expect(page).to_have_url(re.compile('#sports$'))
            page.wait_for_function('document.activeElement.id=="sports"')
            nav.get_by_role('link',name='Home',exact=True).click()
            page.wait_for_function('scrollY<1')
            expect(page.locator('#main-content')).to_be_focused()
            before=len(page.evaluate('window.homeScrollCalls'))
            page.get_by_role('link',name='Photo credits').click()
            expect(page).to_have_url(re.compile('/credits$'))
            assert len(page.evaluate('window.homeScrollCalls'))==before
            return {'motion':motion,'earlyY':early,'finalY':final,'calls':calls,'historyDelta':1}
        for motion in ['reduce','no-preference']:
            case(f'smooth-navigation-{motion}',lambda m=motion:smooth_navigation(m))

        def catalog_state(mode):
            _,page=page_for(api_mode=mode)
            trigger=page.get_by_role('button',name='Sport All sports')
            if mode=='retry':
                notice=page.get_by_text('Sport filters are temporarily unavailable. You can still browse classes.')
                expect(notice).to_be_visible()
                notice.locator('..').get_by_role('button',name='Retry',exact=True).click()
                expect(notice).to_have_count(0)
            trigger.click()
            expect(page.get_by_role('option')).to_have_count(1 if mode=='empty' else 7)
            page.keyboard.press('Escape')
            return 'Fixture '+mode+': API-driven count, independent fallback and Retry'
        for mode in ['empty','retry']:
            case(f'catalog-{mode}',lambda m=mode:catalog_state(m))

        def live_api():
            context=browser.new_context(viewport={'width':1440,'height':900},reduced_motion='reduce')
            context.grant_permissions(['local-network-access'], origin=args.live_origin or args.base)
            if args.live_origin:
                origin=args.live_origin.rstrip('/')
                def frontend_asset(route):
                    response=route.fetch(url=args.base.rstrip('/')+route.request.url[len(origin):])
                    route.fulfill(response=response)
                context.route(origin+'/**',frontend_asset)
            context.route('https://images.unsplash.com/**',lambda route:route.abort())
            context.route('https://assets.mixkit.co/**',lambda route:route.abort())
            page=context.new_page()
            responses={}
            errors=[]
            def observed(response):
                if '/api/v1/' in response.url:
                    path=urlparse(response.url).path.split('/api/v1')[-1]
                    try:
                        body=response.json()
                        responses[path]={'status':response.status,'count':len(body) if isinstance(body,list) else None,
                            'allowOrigin':response.headers.get('access-control-allow-origin')}
                        if path=='/facilities/public':
                            responses[path]['types']={kind:sum(row['facilityType']==kind for row in body)
                                for kind in ['COURT','FIELD','HALL','STUDIO','POOL']}
                        if path=='/packages': responses[path]['ids']=[row['id'] for row in body]
                        if path=='/sports': responses[path]['names']=[row['name'] for row in body]
                    except Exception as error: errors.append(str(error))
            page.on('response',observed)
            page.on('requestfailed',lambda request:errors.append({'url':request.url,'failure':request.failure,'origin':request.headers.get('origin')}) if '/api/v1/' in request.url else None)
            page.on('console',lambda message:errors.append(message.text) if message.type=='error' and ('CORS' in message.text or 'fetch' in message.text) else None)
            page.on('pageerror',lambda error:errors.append(str(error)))
            page.goto(args.live_origin or args.base,wait_until='networkidle')
            for path in ['/sports','/packages','/coaches/public','/facilities/public','/membership-cards/tiers']:
                assert path in responses and responses[path]['status']==200, {'responses':responses,'errors':errors}
            assert not errors,errors
            assert page.get_by_text(re.compile('temporarily unavailable')).count()==0
            trigger=page.get_by_role('button',name='Sport All sports'); trigger.click()
            expect(page.get_by_role('option')).to_have_count(responses['/sports']['count']+1)
            options=page.get_by_role('option').all_inner_texts()
            assert options==['All sports',*responses['/sports']['names']]
            snapshot(page,'live-api-sports','light',1440)
            page.keyboard.press('Escape')
            nav=page.get_by_role('navigation',name='Home navigation')
            nav.get_by_role('link',name='Sports',exact=True).hover()
            header_sports=nav.get_by_role('list',name='Sports destinations').get_by_role('link')
            assert header_sports.all_inner_texts()==responses['/sports']['names']
            snapshot(page,'live-api-header-sports','light',1440)
            page.mouse.move(1400,800)
            expect(page.locator('#coaches button[aria-label^="View profile of "]')).to_have_count(responses['/coaches/public']['count'])
            summary=page.locator('#facilities .home-head__sub').inner_text()
            nouns={'COURT':'court','FIELD':'field','HALL':'hall','STUDIO':'studio','POOL':'pool'}
            parts=[f"{count} {nouns[kind]}{'s' if count!=1 else ''}" for kind,count in responses['/facilities/public']['types'].items() if count]
            expected_summary=(', '.join(parts[:-1])+' and '+parts[-1] if len(parts)>1 else parts[0])+', one membership card.' if parts else 'Every court and pool, one membership card.'
            assert summary==expected_summary
            package_links=page.locator('#packages a[aria-label^="View options for "]').count()
            if responses['/packages']['count']: assert package_links>0
            for link in page.locator('#packages a[aria-label^="View options for "]').all():
                assert int(link.get_attribute('href').split('/')[-1]) in responses['/packages']['ids']
            assert page.locator('.home-cards').get_by_text('Save on every sport package.').count()==1
            return {'responses':responses,'options':options,'coachCards':responses['/coaches/public']['count'],
                'browserOrigin':args.live_origin or args.base,'frontendAssetSource':args.base,'apiInterception':False,
                'facilitySummary':summary,'livePackageLinks':package_links,
                'tiersRendering':'Current Home renders a generic membership message, not individual benefit rows; loaded without error only'}
        if args.live_api: case('live-api-browser-integration',live_api)

        def nav_contract(width, theme):
            _, page = page_for(width=width, height=844 if width < 768 else 900, theme=theme)
            nav = page.get_by_role('navigation', name='Home navigation')
            samples = []
            for state, y in [('transparent', 0), ('sticky', 1000)]:
                page.evaluate('(y) => scrollTo(0,y)', y)
                if width <= 1100:
                    page.get_by_role('button', name='Menu', exact=True).click()
                labels = nav.locator('.home-nav__link')
                expect(labels).to_have_text(['Home', 'Sports', 'Coaches', 'Packages', 'FAQ'])
                before = labels.evaluate_all('(links)=>links.map(e=>e.getBoundingClientRect().toJSON())')
                styles = labels.evaluate_all('''(links)=>links.map(e=>{const s=getComputedStyle(e,'::after');
                    return {height:s.height,background:s.backgroundColor,transform:s.transform,bottom:s.bottom,left:s.left,right:s.right,content:s.content}})''')
                assert all(s == styles[0] for s in styles), styles
                assert styles[0]['height'] == '2px' and styles[0]['transform'] == 'matrix(1, 0, 0, 1, 0, 0)', styles
                assert styles[0]['background'] != 'rgba(0, 0, 0, 0)'
                coaches = nav.get_by_role('link', name='Coaches', exact=True)
                coaches.hover()
                assert coaches.get_attribute('aria-expanded') is None and coaches.get_attribute('aria-haspopup') is None
                assert nav.get_by_role('list', name='Coaches destinations', include_hidden=True).count() == 0
                assert nav.get_by_role('button', name=re.compile('Coaches destinations')).count() == 0
                for label in ['Sports', 'Packages', 'FAQ']:
                    page.mouse.move(width-2, page.viewport_size['height']-2)
                    nav.get_by_role('link', name=label, exact=True).hover()
                    expect(nav.get_by_role('button',name=f'Toggle {label} destinations')).to_have_attribute('aria-expanded','true')
                    children = nav.get_by_role('list', name=f'{label} destinations').get_by_role('link')
                    assert not any(re.match(r'All (sports|coaches|packages|faqs?|questions)', text, re.I) for text in children.all_inner_texts())
                    assert children.first.evaluate("e=>getComputedStyle(e,'::after').content") == 'none'
                after = labels.evaluate_all('(links)=>links.map(e=>e.getBoundingClientRect().toJSON())')
                assert all(abs(a[k]-b[k]) < 1 for a,b in zip(before,after) for k in ['x','y','width','height']), (before,after)
                assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
                samples.append({'state':state,'styles':styles,'labelBoxes':after})
                page.mouse.move(width-2, page.viewport_size['height']-2)
                snapshot(page, f'r4-nav-{state}', theme, width)
                if width <= 1100:
                    page.get_by_role('button', name='Menu', exact=True).click()
            return samples
        for theme in ['light','dark']:
            for width in [320,390,768,1440]:
                case(f'r4-nav-contract-{theme}-{width}', lambda w=width,t=theme:nav_contract(w,t))

        def direct_parents(mode):
            width = 390 if mode == 'touch' else 1440
            _, page = page_for(width=width, touch=mode=='touch')
            nav = page.get_by_role('navigation',name='Home navigation')
            trace = []
            for label, target in [('Coaches','coaches'),('Sports','sports'),('Packages','packages'),('FAQ','faq')]:
                if mode == 'touch':
                    page.get_by_role('button',name='Menu',exact=True).tap()
                link = nav.get_by_role('link',name=label,exact=True)
                if mode == 'keyboard':
                    for _ in range(120):
                        if link.evaluate('e=>e===document.activeElement'): break
                        page.keyboard.press('Tab')
                    expect(link).to_be_focused()
                    assert link.evaluate('e=>getComputedStyle(e).outlineStyle') != 'none'
                    page.keyboard.press('Enter')
                elif mode == 'touch': link.tap()
                else: link.click()
                expect(page).to_have_url(re.compile('#'+target+'$'))
                expect(page.locator('#'+target)).to_be_focused()
                assert page.locator('#'+target).bounding_box()['y'] >= page.locator('.home-header').bounding_box()['height']
                trace.append({'label':label,'hash':page.evaluate('location.hash')})
            snapshot(page,'r4-direct-'+mode,'light',width)
            return trace
        for mode in ['mouse','keyboard','touch']:
            case('r4-direct-parents-'+mode, lambda m=mode:direct_parents(m))

        def api_header():
            _,page=page_for(growth=24)
            nav=page.get_by_role('navigation',name='Home navigation')
            nav.get_by_role('link',name='Sports',exact=True).hover()
            links=nav.get_by_role('list',name='Sports destinations').get_by_role('link')
            expect(links).to_have_count(30)
            assert links.all_inner_texts()==NAMES+[f'Future sport {i+1}' for i in range(24)]
            links.last.click()
            expect(page).to_have_url(re.compile('#finder$'))
            expect(page.get_by_role('button',name='Sport Future sport 24')).to_be_visible()
            page.get_by_role('button',name='Sport Future sport 24').click()
            page.get_by_role('option',name='All sports',exact=True).click()
            expect(page.get_by_role('button',name='Sport All sports')).to_be_focused()
            snapshot(page,'r4-api-sport-reset','light',1440)
            return 'Header follows 30 API records; API-only sport presets finder; All sports resets independently'
        case('r4-api-header-and-filter-reset',api_header)

        def modal_geometry(width, height, theme, classic=False, stable=False):
            _,page=page_for(width=width,height=height,theme=theme,touch=width<768,classic=classic)
            page.evaluate('''() => {
                document.body.style.setProperty('overflow-y','auto','important');
                document.documentElement.style.setProperty('scrollbar-gutter','auto','important');
            }''')
            if stable:
                page.evaluate("document.documentElement.style.setProperty('scrollbar-gutter','stable both-edges','important')")
            opener=page.get_by_role('link',name='Explore Football',exact=True)
            opener.scroll_into_view_if_needed()
            page.wait_for_timeout(150)
            def measure():
                return page.evaluate('''() => ({y:scrollY,gap:innerWidth-document.documentElement.clientWidth,
                    body:document.body.style.cssText,root:document.documentElement.style.cssText,
                    boxes:['.home-header','.home-header .brand','#sports .home-wrap','.home-top'].map(s=>({selector:s,...document.querySelector(s).getBoundingClientRect().toJSON()}))})''')
            initial=measure()
            if classic: assert initial['gap']==17,initial
            else: assert initial['gap']==0,initial
            if height==30000: assert page.evaluate('document.documentElement.scrollHeight<=innerHeight')
            trace=[]
            for cycle, dismiss in enumerate(['escape','close','continue','backdrop','escape']):
                opener.focus()
                before=measure()
                if cycle==0: snapshot(page,f'r4-modal-{height}-{classic}-{stable}-before',theme,width)
                opener.click()
                dialog=page.get_by_role('dialog',name='Sign in to continue')
                expect(dialog).to_be_visible()
                opened=measure()
                assert page.evaluate("getComputedStyle(document.body).overflowY")=='hidden'
                assert dialog.evaluate('e=>e.contains(document.activeElement)')
                for key in ['Shift+Tab','Tab','Tab','Tab','Tab']:
                    page.keyboard.press(key)
                    assert dialog.evaluate('e=>e.contains(document.activeElement)')
                page.mouse.move(3,height/2)
                page.mouse.wheel(0,500)
                page.wait_for_timeout(100)
                assert abs(page.evaluate('scrollY')-before['y'])<1
                body=dialog.locator('.modal__body')
                own=body.evaluate('e=>({height:e.clientHeight,scroll:e.scrollHeight})') if body.count() else {'height':0,'scroll':0}
                if own['scroll']>own['height']:
                    body.hover(); page.mouse.wheel(0,300); page.wait_for_timeout(100)
                    assert body.evaluate('e=>e.scrollTop')>0
                    assert abs(page.evaluate('scrollY')-before['y'])<1
                if cycle==0: snapshot(page,f'r4-modal-{height}-{classic}-{stable}-open',theme,width)
                if dismiss=='escape': page.keyboard.press('Escape')
                elif dismiss=='close': dialog.get_by_role('button',name='Close dialog').click()
                elif dismiss=='continue': dialog.get_by_role('button',name='Continue browsing').click()
                else: page.mouse.click(initial['gap']+2,height/2)
                expect(dialog).to_have_count(0)
                expect(opener).to_be_focused()
                after=measure()
                assert before['body']==after['body'] and before['root']==after['root'],(before,after)
                assert abs(after['y']-before['y'])<1
                delta=max(abs(a[k]-b[k]) for stage in [opened,after] for a,b in zip(before['boxes'],stage['boxes']) for k in ['x','width'])
                assert delta<=1,{'delta':delta,'before':before,'opened':opened,'after':after}
                trace.append({'dismiss':dismiss,'delta':delta,'before':before,'open':opened,'after':after,'modalBody':own})
                if cycle==0: snapshot(page,f'r4-modal-{height}-{classic}-{stable}-after',theme,width)
            # A distinct consecutive dialog must inherit no stale lock or focus target.
            contact=page.get_by_role('button',name='Contact reception',exact=True)
            contact.click(); expect(page.get_by_role('dialog')).to_be_visible()
            assert page.evaluate("getComputedStyle(document.body).overflowY")=='hidden'
            page.keyboard.press('Escape'); expect(contact).to_be_focused()
            assert measure()['body']==initial['body'] and measure()['root']==initial['root']
            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
            return {'classic':classic,'stableBothEdges':stable,'initialGap':initial['gap'],'cycles':trace}
        for theme in ['light','dark']:
            for width,height,classic in [(1440,900,True),(768,900,True),(390,420,False),(320,420,False),(1440,30000,False)]:
                case(f'r4-modal-{theme}-{width}-{height}-{classic}',lambda w=width,h=height,t=theme,c=classic:modal_geometry(w,h,t,c))
        case('r4-modal-existing-stable-gutter',lambda:modal_geometry(1440,900,'light',True,True))

        def short_modal_body():
            _,page=page_for(width=320,height=420,touch=True)
            opener=page.get_by_role('button',name='Contact reception',exact=True)
            opener.click()
            page.set_viewport_size({'width':320,'height':160})
            page.wait_for_timeout(200)
            dialog=page.get_by_role('dialog')
            expect(dialog).to_be_visible()
            body=dialog.locator('.modal__body')
            dimensions=body.evaluate('''e=>({height:e.clientHeight,scroll:e.scrollHeight,innerHeight,
                visualHeight:visualViewport.height,dialogMax:getComputedStyle(e.parentElement).maxHeight,
                dialog:e.parentElement.getBoundingClientRect().toJSON()})''')
            assert dimensions['scroll']>dimensions['height'],dimensions
            before=page.evaluate('scrollY')
            body.hover(); page.mouse.wheel(0,300); page.wait_for_timeout(150)
            assert body.evaluate('e=>e.scrollTop')>0
            assert page.evaluate('scrollY')==before
            snapshot(page,'r4-short-modal-body','light',320)
            dialog.get_by_role('button',name='Close dialog').click()
            expect(opener).to_be_focused()
            return dimensions
        case('r4-modal-own-scroll-short-viewport',short_modal_body)

        def real_video():
            _, page = page_for(media=True)
            video = page.locator('.home-hero__video')
            # Real CDN and real decoder; no media prototype overrides or synthetic playing events.
            page.wait_for_function('document.querySelector(".home-hero__video")?.currentTime > 0.1', timeout=45000)
            pause = page.get_by_role('button', name='Pause background video')
            pause.click()
            expect(page.get_by_role('button', name='Play background video')).to_have_attribute('aria-pressed', 'true')
            assert video.evaluate('(e) => e.paused')
            page.get_by_role('button', name='Play background video').click()
            page.wait_for_function('document.querySelector(".home-hero__video")?.paused === false')
            result = video.evaluate('(e) => ({src:e.currentSrc, time:e.currentTime, paused:e.paused, width:e.videoWidth, height:e.videoHeight})')
            snapshot(page, 'real-video', 'light', 1440)
            return result
        case('real-cdn-video-pause-resume', real_video)
        browser.close()
        classic_browser.close()

    report['passed'] = sum(c['status'] == 'PASS' for c in report['cases'])
    report['failed'] = sum(c['status'] == 'FAIL' for c in report['cases'])
    (out / 'results.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(f"Results: {report['passed']} passed, {report['failed']} failed, {len(report['notRun'])} NOT_RUN; SHA {sha}; dirty={dirty}")
    raise SystemExit(1 if report['failed'] else 0)


if __name__ == '__main__':
    main()
