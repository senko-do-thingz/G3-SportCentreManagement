# FE-HOME - Home redesign test cases

Scope: public Home page at `/` (frontend only). Run `npm install` then `npm run dev` in `frontend`, open http://localhost:5173. Start the backend for live packages and card tiers.

| ID | Area | Steps | Expected result | Automated |
|---|---|---|---|---|
| HOME-01 | Sections | Open `/` as a guest | Hero, finder, Our features, Choose your sport, Train with the best, Choose how you train, Membership cards, Got questions?, Ready to play? and footer are shown | Yes |
| HOME-02 | Catalogue calls | Open `/` as a guest | Only GET /sports, /packages and /membership-cards/tiers, without Authorization | Yes |
| HOME-03 | Header | Check the header | S logo, Home, Sports, Coaches, Packages, FAQ, moon or sun switch, Log in, Join now | Manual |
| HOME-04 | Hero actions | Click Join now in the hero as guest; click Find a class | Registration page; page scrolls to the class finder | Manual |
| HOME-05 | Sport strip | Click Next sports, then Previous sports twice | Football/Badminton, then Badminton/Basketball, then Tennis/Football; counter, bar and photo follow | Yes |
| HOME-06 | Class finder | Pick Sport, Level, Training, click Find classes as guest | Login, then Find Classes with sportId, level and format | Yes (sport, level) |
| HOME-07 | Staff account | Sign in as Manager, click Find classes on Home | Dialog "Member booking and purchases" with Open my workspace | Yes |
| HOME-08 | Sport tiles | Backend running, click a sport tile | Goes to `/member/classes?sportId=<id>` | Yes |
| HOME-09 | Photos | Open `/` with internet access | Hero, strip, six sports, three coaches and Ready to play show sharp photos | Manual |
| HOME-10 | Photo fallback | DevTools Network: block `images.unsplash.com`, reload | Every photo switches to the bundled image, no broken icons | Yes (logic) |
| HOME-11 | Coaches | Click Daniel Smith photo, then All coaches; press Escape each time | Dialogs open and close, focus returns | Yes |
| HOME-12 | Packages | Backend with self-training, coach-led and single visit packages | Cards in order Single visit, Coach-led (Popular, highlighted), Self-training; View options opens the matching package | Yes |
| HOME-13 | Packages error | Stop the backend, reload | "Current packages and prices are temporarily unavailable." with Retry | Yes |
| HOME-14 | Membership strip | Open Home | "Save on every sport package."; View cards opens membership cards | Yes |
| HOME-15 | FAQ | Open Got questions?, click "Can I come for a single visit?" | Four items, first open by default, clicked item expands | Yes |
| HOME-16 | Theme switch | Click the moon icon, then the sun icon | Page switches to dark, then light; choice is kept after reload and on Login | Yes |
| HOME-17 | Footer | Click Find a class, Packages, FAQ, Back to top; Contact reception; AI Assistant | Links scroll to sections; dialogs open | Yes (dialogs) |
| HOME-18 | Light and dark | Compare both modes | Royal and Gold palette: navy hero, royal blue buttons, gold highlights; light uses white and pale bands, dark uses near-black navy with raised cards; sun icon in light, moon icon in dark | Manual |
| HOME-19 | Mobile | Width 390px, open Menu, press Escape | Navigation opens and closes, no horizontal scroll | Yes (keyboard) / Manual (layout) |
| HOME-20 | Reduced motion | Enable reduced motion in the OS | Card lift and photo zoom are disabled | Manual |
