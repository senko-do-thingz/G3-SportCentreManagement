"""Direct rendered Home parity. Invoked by payment-home-check.py --round round-02 --parity."""
import hashlib
import json
from datetime import datetime, timezone
from playwright.sync_api import expect

PROPS = ['background-color', 'color', 'border-top-color', 'border-top-width', 'border-top-style',
         'border-radius', 'font-family', 'font-size', 'font-style', 'font-weight', 'line-height',
         'letter-spacing', 'padding', 'gap', 'box-shadow', 'outline-color', 'outline-style', 'outline-width', 'outline-offset']
LOGO_PROPS = PROPS + ['width', 'height', 'display', 'align-items']


def styles(locator, props=PROPS):
    return locator.first.evaluate('(e,props)=>Object.fromEntries(props.map(p=>[p,getComputedStyle(e).getPropertyValue(p)]))', props)


def run_parity(args, out, integration, browser, open_page, report, contrast):
    report.update({'comparisons': [], 'mismatches': [], 'negativeControls': [], 'screenshots': [],
                   'nativeZoom': 'Separate native zoom result or explicit unsupported result is recorded locally.'})
    routes = {'UI-27': '/member/payments/52', 'UI-28': '/member/payments/52/status',
              'UI-32': '/reception/refunds/11?memberId=2'}
    input_refs = {}
    semantic_refs = {}

    def save():
        (integration / 'UI-27-UI-28-UI-32-f-i-round-02-computed-styles.json').write_text(json.dumps(report, indent=2), encoding='utf-8')

    def check(name, value, detail=None):
        report['checks'].append({'name': name, 'pass': bool(value), 'detail': detail})
        save()
        if not value: raise AssertionError(f'{name}: {detail}')

    def compare(code, route, theme, width, component, actual, expected):
        row = {'ui': code, 'route': route, 'theme': theme, 'width': width, 'component': component,
               'actual': actual, 'expected': expected}
        report['comparisons'].append(row)
        for prop, value in expected.items():
            if actual.get(prop) != value:
                report['mismatches'].append(dict(ui=code, route=route, theme=theme, width=width,
                    selector=component, property=prop, actual=actual.get(prop), expected=value))
        save()

    def shot(code, page, theme, state, role, locator=None, full=False):
        width = page.viewport_size['width']
        size = 'desktop-1440' if width == 1440 else 'tablet-768' if width == 768 else f'mobile-{width}'
        prefix = f'{code}-f-i-round-02-after' if code != 'HOME' else 'HOME-reference-round-02'
        folder = out / f'{code}-f-i/round-02/screenshots' if code != 'HOME' else integration / 'screenshots'
        file = folder / f'{prefix}-{theme}-{size}-{state}.png'
        if locator is not None: locator.first.screenshot(path=str(file), animations='disabled')
        else: page.screenshot(path=str(file), full_page=full, animations='disabled')
        report['screenshots'].append({'ui': code, 'path': str(file.relative_to(out)), 'sha': report['sha'],
            'dirty': report['dirty'], 'route': page.url.split(args.base)[-1], 'role': role,
            'viewport': page.viewport_size, 'theme': theme, 'state': state, 'browser': report['browser'],
            'mode': report['mode'], 'time': datetime.now(timezone.utc).isoformat(), 'masks': 'none; synthetic data',
            'hash': hashlib.sha256(file.read_bytes()).hexdigest()})
        save()

    def states(page, selector):
        element = page.locator(selector).first
        base = styles(element)
        element.hover()
        page.wait_for_timeout(200)
        hover = styles(element)
        page.mouse.move(0, 0)
        page.wait_for_timeout(200)
        element.focus()
        page.keyboard.press('Tab')
        page.keyboard.press('Shift+Tab')
        page.wait_for_timeout(200)
        focus = styles(element)
        element.evaluate('(e)=>e.disabled=true')
        disabled = styles(element, ['opacity', 'cursor']) if element.evaluate('(e)=>e.tagName==="BUTTON"') else None
        element.evaluate('(e)=>e.disabled=false')
        return {'default': base, 'hover': hover, 'focus': focus, 'disabled': disabled}

    def theme_states(page):
        toggle = page.locator('.home-theme').first
        normal = styles(toggle, LOGO_PROPS)
        toggle.hover(); page.wait_for_timeout(250)
        hover = styles(toggle, LOGO_PROPS)
        page.mouse.move(0, 0); page.wait_for_timeout(250)
        toggle.focus(); page.keyboard.press('Tab'); page.keyboard.press('Shift+Tab')
        return {'default': normal, 'hover': hover, 'focus': styles(toggle, LOGO_PROPS)}

    try:
        for width in [1440, 768, 390]:
            for theme in ['light', 'dark']:
                ctx, home, _, _, _ = open_page('HOME', '/', width, theme)
                home.locator('.home-header .brand').wait_for()
                home.evaluate('document.fonts.ready')
                shot('HOME', home, theme, 'first-viewport', 'GUEST')
                shot('HOME', home, theme, 'full-page', 'GUEST', full=True)
                shot('HOME', home, theme, 'logo', 'GUEST', home.locator('.home-header .brand'))
                shot('HOME', home, theme, 'theme-toggle', 'GUEST', home.locator('.home-theme'))
                refs = {selector: styles(home.locator(selector), LOGO_PROPS) for selector in
                        ['.home-header .brand', '.home-header .brand__mark', '.home-header .brand__name', '.home-theme']}
                logo_html = home.locator('.home-header .brand').inner_html()
                toggle_html = home.locator('.home-theme svg').inner_html()
                toggle_states = theme_states(home)
                home.locator('.home-select__button').first.click()
                expect(home.locator('.home-select__option--active').first).to_be_visible()
                selected = styles(home.locator('.home-select__option--active'), ['background-color', 'color', 'border-radius'])
                home.keyboard.press('Escape')
                card = styles(home.locator('.home-plan').first, ['background-color', 'border-top-color', 'border-top-width', 'border-radius', 'padding'])
                compact_heading = styles(home.locator('.home-coach-card__body h3'))
                nav_props = ['font-family', 'font-size', 'font-weight', 'font-style', 'letter-spacing', 'line-height', 'border-radius', 'padding']
                navigation_type = styles(home.locator('.home-nav__link'), nav_props)
                primary = states(home, '.home-plan .btn--primary')
                disabled_button = states(home, '.home-footer .btn--primary')['disabled']
                ctx.route('**/api/v1/packages', lambda r: r.fulfill(status=503, json={'message': 'Synthetic catalogue error'}, content_type='application/json'))
                home.reload()
                expect(home.locator('.home-pricing .home-catalog-notice .btn--secondary')).to_be_visible()
                secondary = states(home, '.home-pricing .home-catalog-notice .btn--secondary')
                # Retry uses the approved sm size. Transactional md controls use the same Home skin
                # and the existing md geometry, measured from the real Home plan CTA.
                for state in ['default', 'hover', 'focus']:
                    for prop in ['font-size', 'line-height', 'padding', 'letter-spacing']:
                        secondary[state][prop] = primary[state][prop]
                report.setdefault('referenceMappings', []).append({'theme': theme, 'width': width,
                    'secondarySkin': '.home-pricing .home-catalog-notice .btn--secondary',
                    'secondaryMdSize': '.home-plan .btn--primary (shared btn--md size)',
                    'modal': 'Home informational Modal md; authenticated Home CTA and approved shared Button md'})
                home.get_by_role('link', name='Membership offers', exact=True).click()
                expect(home.get_by_role('dialog')).to_be_visible()
                home.mouse.move(0, 0)
                home.wait_for_timeout(200)
                dialog = {selector: styles(home.locator(selector)) for selector in
                          ['.modal', '.modal__title', '.modal__close', '.modal__header', '.modal__footer']}
                # Width is content/size dependent. Compare actual common modal geometry, not its size variant.
                shot('HOME', home, theme, 'approved-dialog', 'GUEST')
                home.keyboard.press('Escape')
                home.get_by_role('button', name='Contact reception', exact=True).click()
                expect(home.get_by_role('dialog')).to_be_visible()
                dialog['.modal']['width'] = styles(home.locator('.modal'), ['width'])['width']
                shot('HOME', home, theme, 'approved-md-dialog', 'GUEST')
                home.keyboard.press('Escape')
                ctx.unroute_all(behavior='ignoreErrors'); ctx.close()
                shared_ctx, shared, _, _, _ = open_page('HOME', '/login', width, theme)
                shared.goto(args.reference_dev_base + '/ui-kit')
                expect(shared.get_by_role('heading', name='UI kit', exact=True)).to_be_visible()
                report['sharedReference'] = {'url': args.reference_dev_base + '/ui-kit', 'sourceSha': report['sha'],
                    'mode': 'Same-source development-only component catalogue; all target and Home routes use production preview.'}
                modal_actions = {variant: states(shared, f'.btn--{variant}.btn--md') for variant in ['primary', 'secondary']}
                semantic = {selector: styles(shared.locator(selector), ['background-color', 'color', 'border-radius', 'padding', 'border-top-color', 'border-top-width'])
                            for selector in ['.notice--info', '.badge--warning', '.badge--success']}
                semantic_refs[(width, theme)] = semantic
                shared_ctx.unroute_all(behavior='ignoreErrors'); shared_ctx.close()
                auth_ctx, auth, _, _, _ = open_page('HOME', '/login', width, theme)
                input_refs[(width, theme)] = {'default': styles(auth.locator('input[type="email"]'))}
                auth.locator('input[type="email"]').focus()
                auth.wait_for_timeout(200)
                input_refs[(width, theme)]['focus'] = styles(auth.locator('input[type="email"]'))
                auth_ctx.unroute_all(behavior='ignoreErrors'); auth_ctx.close()
                staff_ctx, staff_home, _, _, _ = open_page('UI-32', '/', width, theme)
                staff_home.get_by_role('button', name='View single visit options', exact=True).click()
                expect(staff_home.get_by_role('dialog')).to_be_visible()
                staff_home.mouse.move(0, 0)
                shot('HOME', staff_home, theme, 'approved-staff-dialog', 'RECEPTIONIST')
                compare('HOME', '/', theme, width, 'Home staff dialog primary matches shared md Button',
                        styles(staff_home.locator('.modal .btn--primary')), modal_actions['primary']['default'])
                staff_ctx.unroute_all(behavior='ignoreErrors'); staff_ctx.close()

                for code, route in routes.items():
                    ctx, page, model, requests, role = open_page(code, route, width, theme)
                    header = page.locator('.payment-workspace-header')
                    check(f'{code} no legacy header dropdown {theme} {width}', header.locator('select').count() == 0)
                    expect(page.get_by_role('button', name=f"Switch to {'dark' if theme == 'light' else 'light'} mode", exact=True)).to_be_visible()
                    check(f'{code} canonical Brand DOM identity', header.locator('.brand').inner_html() == logo_html)
                    report.setdefault('brandIdentities', []).append({'ui': code, 'theme': theme, 'width': width,
                        'canonicalSource': 'frontend/src/components/Brand.tsx; HomeHeader renders Brand compact',
                        'assetType': 'Original React text lockup; no separate image/SVG asset exists in approved Home',
                        'homeMarkupSha256': hashlib.sha256(logo_html.encode()).hexdigest(),
                        'targetMarkupSha256': hashlib.sha256(header.locator('.brand').inner_html().encode()).hexdigest()})
                    check(f'{code} canonical ThemeToggle icon identity', header.locator('.home-theme svg').inner_html() == toggle_html)
                    check(f'{code} actual display font loaded', page.evaluate('(font)=>document.fonts.check(font)', 'italic 800 30px "Be Vietnam Pro"'))
                    check(f'{code} canonical italic font face loaded', page.evaluate('()=>Array.from(document.fonts).some(f=>f.family.includes("Be Vietnam Pro") && f.weight==="800" && f.style==="italic" && f.status==="loaded")'))
                    check(f'{code} no legacy tagline', header.locator('.brand__tagline').count() == 0)
                    check(f'{code} theme touch target', header.locator('.home-theme').evaluate('(e)=>{const r=e.getBoundingClientRect();return r.width>=44 && r.height>=44}'))
                    for selector, expected in refs.items():
                        target = selector.replace('.home-header ', '')
                        compare(code, route, theme, width, target, styles(header.locator(target), LOGO_PROPS), expected)
                    compare(code, route, theme, width, '.card', styles(page.locator('.card'), list(card)), card)
                    compare(code, route, theme, width, 'Compact card heading - Home coach card', styles(page.locator('.card__title')), compact_heading)
                    if code != 'UI-32':
                        compare(code, route, theme, width, 'Payment pages navigation type', styles(page.locator('.payment-nav__link'), nav_props), navigation_type)
                        compare(code, route, theme, width, 'Member role navigation type', styles(page.locator('.member-nav__link'), nav_props), navigation_type)
                    selection = page.locator('.payment-methods__option:has(input:checked)' if code == 'UI-27' else '.refund-steps__step[data-state="current"]')
                    if selection.count(): compare(code, route, theme, width, 'Home selected option palette', styles(selection, list(selected)), selected)
                    check(f'{code} no page overflow', page.evaluate('document.documentElement.scrollWidth <= innerWidth'))
                    body = styles(page.locator('.payment-workspace'), ['color', 'background-color'])
                    check(f'{code} body contrast', contrast(body['color'], body['background-color']) >= 4.5, body)
                    header_colors = styles(header, ['background-color'])
                    name_color = styles(header.locator('.user-menu__name'), ['color'])
                    check(f'{code} account text contrast', contrast(name_color['color'], header_colors['background-color']) >= 4.5)
                    shot(code, page, theme, 'first-viewport', role)
                    shot(code, page, theme, 'full-page', role, full=True)
                    shot(code, page, theme, 'logo', role, header.locator('.brand'))
                    shot(code, page, theme, 'theme-toggle', role, header.locator('.home-theme'))
                    actual_toggle_states = theme_states(page)
                    for state, expected in toggle_states.items():
                        compare(code, route, theme, width, f'ThemeToggle:{state}', actual_toggle_states[state], expected)
                    if code == 'UI-27': shot(code, page, theme, 'payment-method-form', role, page.locator('.payment-method-form'))
                    if code == 'UI-32': page.get_by_label('I have returned the approved amount').check()
                    actual_primary = states(page, '.payment-workspace .btn--primary')
                    check(f'{code} primary contrast', contrast(actual_primary['default']['color'], actual_primary['default']['background-color']) >= 4.5)
                    for selector, expected in semantic.items():
                        if page.locator(selector).count():
                            compare(code, route, theme, width, selector, styles(page.locator(selector), list(expected)), expected)
                    for state in primary:
                        if state != 'disabled' or actual_primary[state] is not None:
                            compare(code, route, theme, width, f'primary:{state}', actual_primary[state], disabled_button if state == 'disabled' else primary[state])
                    for variant in ['secondary', 'ghost']:
                        locator = page.locator(f'.payment-workspace .btn--{variant}')
                        if locator.count():
                            values = states(page, f'.payment-workspace .btn--{variant}')
                            for state in secondary:
                                if state != 'disabled' or values[state] is not None:
                                    compare(code, route, theme, width, f'{variant}:{state}', values[state], disabled_button if state == 'disabled' else secondary[state])
                    shot(code, page, theme, 'buttons-focus', role, page.locator('.payment-workspace .btn--primary'))
                    # Use the real shared toggle. Theme must survive navigation, Back and reload.
                    page.evaluate('window.scrollTo(0,0)')
                    toggle = page.get_by_role('button', name=f"Switch to {'dark' if theme == 'light' else 'light'} mode", exact=True)
                    toggle.focus(); page.keyboard.press('Space')
                    switched = 'dark' if theme == 'light' else 'light'
                    check('Shared toggle changes resolved theme', page.locator('html').get_attribute('data-theme') == switched)
                    check('Shared toggle persists explicit preference', page.evaluate('localStorage.getItem("sportify.themePreference")') == switched)
                    for selector in ['.home-header .brand__mark', '.home-header .brand__name']:
                        target = selector.replace('.home-header ', '')
                        compare(code, route, switched, width, f'after toggle {target}', styles(header.locator(target), LOGO_PROPS), refs[selector])
                    page.goto(args.base + '/')
                    check('Home uses the workspace theme', page.locator('html').get_attribute('data-theme') == switched)
                    page.go_back(); page.reload()
                    check('Back and reload preserve the theme', page.locator('html').get_attribute('data-theme') == switched)
                    page.get_by_role('button', name=f'Switch to {theme} mode', exact=True).click()
                    expect(page.locator('html')).to_have_attribute('data-theme', theme)
                    # Negative controls run the same predicate used by the parity gate, then restore exact DOM/style bytes.
                    if width == 1440 and theme == 'light':
                        button = page.locator('.payment-workspace .btn--primary').first
                        old = button.get_attribute('style')
                        button.evaluate('(e)=>{e.style.borderRadius="9px";e.style.backgroundColor="rgb(1, 2, 3)"}')
                        mutated = styles(button)
                        check('Wrong button radius/color fails parity predicate', mutated['border-radius'] != primary['default']['border-radius'] and mutated['background-color'] != primary['default']['background-color'])
                        start = len(report['mismatches'])
                        compare(code, route, theme, width, 'negative-control button', mutated, primary['default'])
                        negative_comparison = report['comparisons'].pop()
                        failures = report['mismatches'][start:]
                        report['mismatches'] = report['mismatches'][:start]
                        check('Actual parity comparator rejects mutated control', any(f['property'] == 'border-radius' for f in failures) and any(f['property'] == 'background-color' for f in failures))
                        button.evaluate('(e,s)=>s===null?e.removeAttribute("style"):e.setAttribute("style",s)', old)
                        header = page.locator('.payment-workspace-header')
                        header.evaluate('(e)=>{const s=document.createElement("select");s.setAttribute("aria-label","Appearance");s.id="parity-negative-dropdown";e.append(s)}')
                        check('Legacy dropdown fails header predicate', header.locator('select').count() != 0)
                        page.locator('#parity-negative-dropdown').evaluate('(e)=>e.remove()')
                        report['negativeControls'].append({'ui': code, 'wrongRadiusAndColorRejected': True, 'legacyDropdownRejected': True, 'actualComparatorFailures': failures, 'comparison': negative_comparison, 'restored': True})
                    if width < 1101:
                        menu = page.get_by_role('button', name='Menu', exact=True)
                        menu.click()
                        navigation = page.locator('#member-navigation' if code != 'UI-32' else '#staff-menu')
                        expect(navigation).to_be_visible()
                        shot(code, page, theme, 'role-navigation-open', role)
                        navigation.get_by_role('link').first.focus()
                        page.keyboard.press('Escape')
                        expect(menu).to_be_focused()
                    if code == 'UI-28':
                        copy = page.get_by_role('button', name='Copy transfer content', exact=True)
                        expected = copy.locator('..').locator('.copy-value__text').inner_text()
                        copy.click()
                        check('Real clipboard equals displayed transfer content', page.evaluate('navigator.clipboard.readText()') == expected)
                        shot(code, page, theme, 'copied-transfer', role)
                        model['status'] = 'SUCCESS'
                        page.get_by_role('button', name='Refresh status', exact=True).click()
                        expect(page.get_by_role('heading', name='Payment confirmed', exact=True)).to_be_visible()
                        shot(code, page, theme, 'confirmed', role)
                    if code == 'UI-32':
                        page.get_by_label('I have returned the approved amount').check()
                        trigger = page.get_by_role('button', name='Record completed refund', exact=True)
                        trigger.click()
                        modal = page.get_by_role('dialog')
                        page.mouse.move(0, 0)
                        page.wait_for_timeout(200)
                        for selector, expected in dialog.items():
                            compare(code, route, theme, width, selector, styles(page.locator(selector), list(expected)), expected)
                        for variant, expected in modal_actions.items():
                            actual = states(page, f'.modal .btn--{variant}')
                            for state in expected:
                                compare(code, route, theme, width, f'modal {variant}:{state}', actual[state], expected[state])
                        shot(code, page, theme, 'refund-confirmation-modal', role)
                        for key in ['Tab', 'Tab', 'Tab', 'Shift+Tab']:
                            page.keyboard.press(key)
                            check('Payout modal traps keyboard focus', modal.evaluate('(e)=>e.contains(document.activeElement)'))
                        page.keyboard.press('Escape')
                        expect(trigger).to_be_focused()
                        check('Escape dispatches no payout', not any(r['method'] == 'PUT' for r in requests))
                        trigger.click(); modal.get_by_role('button', name='Cancel', exact=True).click()
                        expect(trigger).to_be_focused()
                        trigger.click(); modal.get_by_role('button', name='Yes, record').click()
                        expect(page.get_by_role('heading', name='Refund Completed', exact=True)).to_be_visible()
                        check('Exactly one payout PUT', sum(r['method'] == 'PUT' for r in requests) == 1)
                        shot(code, page, theme, 'refund-completed', role)
                    ctx.unroute_all(behavior='ignoreErrors'); ctx.close()

        for code, route in routes.items():
            for state in ['loading', 'error', 'empty'] if code == 'UI-32' else ['loading', 'error', 'invalid-link']:
                path = route if state != 'invalid-link' else '/member/payments/invalid' + ('/status' if code == 'UI-28' else '')
                ctx, page, _, _, role = open_page(code, path, 390, 'dark', state)
                shot(code, page, 'dark', state, role)
                ctx.unroute_all(behavior='ignoreErrors'); ctx.close()
            ctx, page, _, _, role = open_page(code, route, 390, 'system')
            page.emulate_media(color_scheme='dark')
            expect(page.locator('html')).to_have_attribute('data-theme', 'dark')
            check('Stored system preference follows OS changes', page.evaluate('localStorage.getItem("sportify.themePreference")') == 'system')
            page.get_by_role('button', name='Switch to light mode', exact=True).click()
            page.emulate_media(color_scheme='light'); page.emulate_media(color_scheme='dark')
            expect(page.locator('html')).to_have_attribute('data-theme', 'light')
            check('Explicit preference survives later OS changes', page.evaluate('localStorage.getItem("sportify.themePreference")') == 'light')
            ctx.unroute_all(behavior='ignoreErrors'); ctx.close()

        for width in [1440, 768, 390]:
            for theme in ['light', 'dark']:
                for state, route in [('queues', '/reception/refunds?memberId=2'), ('form', '/reception/refunds?memberId=2&registrationId=1052')]:
                    ctx, page, _, _, role = open_page('UI-32', route, width, theme)
                    if state == 'form':
                        page.get_by_role('button', name='Submit for approval', exact=True).click()
                        expect(page.get_by_text('Reason is required', exact=True)).to_be_visible()
                        amount = page.get_by_label('Refund amount', exact=False)
                        compare('UI-32', route, theme, width, 'Input normal - AuthShell shared field', styles(amount), input_refs[(width, theme)]['default'])
                        amount.focus()
                        page.wait_for_timeout(200)
                        compare('UI-32', route, theme, width, 'Input focus - AuthShell shared field', styles(amount), input_refs[(width, theme)]['focus'])
                    else: expect(page.get_by_role('link', name='Open refund REF-0011')).to_be_visible()
                    shot('UI-32', page, theme, f'refund-{state}', role)
                    shot('UI-32', page, theme, f'refund-{state}-controls', role, page.locator('.reception-form' if state == 'form' else '.table-scroll').first)
                    report['comparisons'].append({'ui': 'UI-32', 'route': route, 'theme': theme, 'width': width,
                        'component': state, 'actual': {selector: styles(page.locator(selector)) for selector in
                        (['.input', '.notice--info'] if state == 'form' else ['.table-scroll', '.table th', '.badge'])},
                'reference': 'Shared Input/Notice/StatusBadge; Home card/table palette; full semantic tokens retained.'})
                    check('Refund form/queue has no page overflow', page.evaluate('document.documentElement.scrollWidth <= innerWidth'))
                    for selector, expected in semantic_refs[(width, theme)].items():
                        if page.locator(selector).count():
                            compare('UI-32', route, theme, width, selector, styles(page.locator(selector), list(expected)), expected)
                    ctx.unroute_all(behavior='ignoreErrors'); ctx.close()
        check('No uncaught browser errors', not report['consoleErrors'], report['consoleErrors'])
        check('All direct Home computed-style comparisons match', not report['mismatches'], report['mismatches'])
    finally:
        save()
    print(json.dumps({'sha': report['sha'], 'checks': len(report['checks']), 'comparisons': len(report['comparisons']),
                      'mismatches': len(report['mismatches']), 'screenshots': len(report['screenshots'])}))
