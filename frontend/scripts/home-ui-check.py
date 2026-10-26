"""Home interaction evidence. Use requirements-visual-check.txt and a Vite preview build.

python scripts/home-ui-check.py --base http://127.0.0.1:4173 --out <private-artifact-directory>
Uses real Playwright mouse, keyboard and touchscreen events. No production account is used.
"""
import argparse
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
    args = parser.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    dirty = bool(subprocess.check_output(['git', 'status', '--porcelain'], text=True).strip())
    report = {'sha': sha, 'dirty': dirty, 'capturedAt': datetime.now(timezone.utc).isoformat(), 'cases': [], 'screenshots': [], 'contrast': [], 'notRun': ['Native browser zoom 200%', 'Assistive technology speech output']}

    with sync_playwright() as pw:
        browser = pw.chromium.launch(channel=args.channel, headless=True)
        report['browser'] = f'{args.channel} {browser.version}'

        def page_for(width=1440, height=900, theme='light', growth=0, touch=False, member=False, media=False):
            context = browser.new_context(viewport={'width': width, 'height': height}, reduced_motion='no-preference' if media else 'reduce', has_touch=touch, is_mobile=touch)
            seed = {'sportify.themePreference': theme}
            if member:
                seed.update({'sportify.accessToken': 'home-ui-fixture', 'sportify.refreshToken': 'fixture-refresh', 'sportify.sessionId': 'home-ui-fixture'})
            context.add_init_script(f'for (const [key, value] of Object.entries({json.dumps(seed)})) if (localStorage.getItem(key) === null) localStorage.setItem(key, value)')
            sports = SPORTS + [{'id': 7 + i, 'code': f'FUTURE_{i}', 'name': f'Future sport {i + 1}'} for i in range(growth)]

            def api(route):
                path = urlparse(route.request.url).path.split('/api/v1')[-1]
                body = {
                    '/sports': sports, '/coaches/public': COACHES, '/packages': [],
                    '/membership-cards/tiers': [], '/facilities/public': [], '/users/me': MEMBER,
                    '/sessions': {'content': [], 'page': 0, 'size': 20, 'totalElements': 0, 'totalPages': 0},
                }.get(path, [])
                route.fulfill(status=200, json=body, headers={'Access-Control-Allow-Origin': '*'})
            context.route('**/api/v1/**', api)
            context.route('https://images.unsplash.com/**', lambda route: route.abort())
            if not media:
                context.route('https://assets.mixkit.co/**', lambda route: route.abort())
            page = context.new_page()
            page.set_default_timeout(7000)
            page.goto(args.base, wait_until='load')
            expect(page.get_by_role('button', name='Sport All sports')).to_be_visible()
            expect(page.get_by_role('button', name='View profile of Demo Coach')).to_have_count(1)
            page.evaluate('document.fonts.ready')
            return context, page

        def case(name, fn):
            start = time.monotonic()
            try:
                details = fn()
                report['cases'].append({'name': name, 'status': 'PASS', 'details': details, 'seconds': round(time.monotonic() - start, 2)})
                print(f'PASS {name}', flush=True)
            except Exception as error:
                report['cases'].append({'name': name, 'status': 'FAIL', 'error': str(error), 'traceback': traceback.format_exc()})
                print(f'FAIL {name}: {error}', flush=True)
            finally:
                for context in list(browser.contexts):
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
            for width in [390, 768, 1440]:
                case(f'header-{theme}-{width}', lambda w=width, t=theme: header_matrix(w, t))

        def nav_interactions():
            _, page = page_for()
            nav = page.get_by_role('navigation', name='Home navigation')
            parent = nav.get_by_role('link', name='Sports', exact=True)
            toggle = nav.get_by_role('button', name='Toggle Sports destinations')
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
            for name in ['Sports', 'Coaches', 'Packages', 'FAQ']:
                parent = nav.get_by_role('link', name=name, exact=True)
                parent.hover()
                links = nav.get_by_role('list', name=f'{name} destinations').get_by_role('link')
                targets = [(link.inner_text(), link.get_attribute('href')) for link in links.all()]
                for label, href in targets:
                    parent.hover()
                    nav.get_by_role('list', name=f'{name} destinations').get_by_role('link', name=label, exact=True).click()
                    expect(page).to_have_url(re.compile(re.escape(href) + '$'))
                    target = page.locator(href)
                    assert target.bounding_box()['y'] >= page.locator('.home-header').bounding_box()['height'] - 1, href
                    expect(nav.get_by_role('button', name=f'Toggle {name} destinations')).to_have_attribute('aria-expanded', 'false')
                    count += 1
            return f'{count} real content destinations navigated'
        case('all-child-destinations', child_destinations)

        def touch_navigation():
            _, page = page_for(width=390, height=844, touch=True)
            page.get_by_role('button', name='Menu', exact=True).tap()
            toggle = page.get_by_role('button', name='Toggle Sports destinations')
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
            if width > 640:
                for selector in ['.home-finder .btn', '.home-select:nth-child(2)', '.home-select:nth-child(3)']:
                    other = page.locator(selector).bounding_box()
                    rect = geometry['rect']
                    assert not (rect['x'] < other['x'] + other['width'] and rect['right'] > other['x'] and rect['y'] < other['y'] + other['height'] and rect['bottom'] > other['y']), selector
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

        def above_placement():
            _, page = page_for()
            page.locator('#finder').evaluate('(e) => scrollBy(0,e.getBoundingClientRect().top-(innerHeight-150))')
            page.get_by_role('button', name='Sport All sports').click()
            menu = page.get_by_role('listbox', name='Sport', exact=True)
            expect(menu).to_have_attribute('data-placement', 'above')
            assert menu.evaluate('(e) => e.scrollHeight <= e.clientHeight + 1')
            snapshot(page, 'filter-above', 'light', 1440)
        case('filter-repositions-above', above_placement)

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
                page.get_by_role('link', name='Sportify Center home').click()
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
            page.get_by_role('button', name='Toggle FAQ destinations').click()
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
            snapshot(page, 'reflow-menu', 'light', 320)
        case('reduced-motion-and-320-reflow', reduced_motion)

        def authenticated_header():
            _, page = page_for(member=True)
            expect(page.get_by_role('link', name='My workspace')).to_have_attribute('href', '/member')
            expect(page.get_by_role('button', name='Log out')).to_be_visible()
            expect(page.get_by_role('navigation', name='Home navigation').get_by_role('link', name='Sports', exact=True)).to_be_visible()
        case('authenticated-public-navigation', authenticated_header)

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

    report['passed'] = sum(c['status'] == 'PASS' for c in report['cases'])
    report['failed'] = sum(c['status'] == 'FAIL' for c in report['cases'])
    (out / 'results.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(f"Results: {report['passed']} passed, {report['failed']} failed, {len(report['notRun'])} NOT_RUN; SHA {sha}; dirty={dirty}")
    raise SystemExit(1 if report['failed'] else 0)


if __name__ == '__main__':
    main()
