# FE-HOME - Home redesign test cases

Scope: public Home page at `/` (frontend only). Run `npm install` then `npm run dev` in `frontend`, open http://localhost:5173. Start the backend for live packages and card tiers.

| ID | Area | Steps | Expected result | Automated |
|---|---|---|---|---|
| HOME-01 | Sections | Open `/` as a guest | Hero, finder, Our features, Choose your sport, Train with the best, Choose how you train, Membership cards, Got questions?, Ready to play? and footer are shown | Yes |
| HOME-02 | Catalogue calls | Open `/` as a guest | Only GET /sports, /packages, /membership-cards/tiers and /coaches/public, without Authorization | Yes |
| HOME-03 | Header | Check the header | S logo, Home, Sports, Coaches, Packages, FAQ, moon or sun switch, Log in, Join now | Manual |
| HOME-04 | Hero actions | Click Join now in the hero as guest; click Find a class | Registration page; page scrolls to the class finder | Manual |
| HOME-05 | Sport strip | Click Next sports, then Previous sports twice | Football/Badminton, then Badminton/Basketball, then Tennis/Football; counter, bar and photo follow | Yes |
| HOME-06 | Class finder | Pick Sport, Level, Training, click Find classes as guest | Login, then Find Classes with sportId, level and format | Yes (sport, level) |
| HOME-07 | Staff account | Sign in as Manager, click Find classes on Home | Dialog "Member booking and purchases" with Open my workspace | Yes |
| HOME-08 | Sport tiles | Backend running, click a sport tile | Goes to `/member/classes?sportId=<id>` | Yes |
| HOME-09 | Photos | Open `/` with internet access | Hero, strip, six sports, three coaches and Ready to play show sharp photos | Manual |
| HOME-10 | Photo fallback | DevTools Network: block `images.unsplash.com`, reload | Every photo switches to the bundled image, no broken icons | Yes (logic) |
| HOME-11 | Coaches | Backend running; open Train with the best; click a coach photo, then All coaches; press Escape each time | Coaches from the database (for example Pham Thu Ha, Le Hoang Nam, Tran Minh Quan) with their sports; dialogs open and close, focus returns | Yes |
| HOME-12 | Packages | Backend with self-training, coach-led and single visit packages | Cards in order Single visit, Coach-led (Popular, highlighted), Self-training; View options opens the matching package | Yes |
| HOME-13 | Packages error | Stop the backend, reload | "Current packages and prices are temporarily unavailable." with Retry | Yes |
| HOME-14 | Membership strip | Open Home | "Save on every sport package."; View cards opens membership cards | Yes |
| HOME-15 | FAQ | Open Got questions?, click "Can I come for a single visit?" | Four items, first open by default, clicked item expands | Yes |
| HOME-16 | Theme switch | Click the moon icon, then the sun icon | Page switches to dark, then light; choice is kept after reload and on Login | Yes |
| HOME-17 | Footer | Click Find a class, Packages, FAQ, Back to top; Contact reception; AI Assistant | Links scroll to sections; dialogs open | Yes (dialogs) |
| HOME-18 | Light and dark | Compare both modes | Royal and Gold palette: navy hero, royal blue buttons, gold highlights; light uses white and pale bands, dark uses near-black navy with raised cards; sun icon in light, moon icon in dark | Manual |
| HOME-19 | Mobile | Width 390px, open Menu, press Escape | Navigation opens and closes, no horizontal scroll | Yes (keyboard) / Manual (layout) |
| HOME-20 | Reduced motion | Enable reduced motion in the OS | Card lift and photo zoom are disabled | Manual |
| HOME-21 | Coach API | `Invoke-RestMethod http://localhost:8080/api/v1/coaches/public` without a token | 200 with id, fullName, headline, specialties, yearsExperience, bio, avatarUrl, sportIds; no email or phone | Yes (PublicCoachControllerTest) |
| HOME-22 | Coach API error | Stop the backend, reload Home | "Coach profiles are temporarily unavailable." with Retry; other sections stay | Yes |
| HOME-23 | Dropdown | Open Sport, Level, Training with mouse and with keyboard (Enter, arrows, Escape) | Themed list in both modes, selected item ticked, focus returns to the field | Yes (keyboard) |
| HOME-24 | Sport strip photo | Click Next sports through all six | Photo keeps the same height for every sport | Manual |
| HOME-25 | FAQ icons | Open and close FAQ items | Plus and close icons are centred in the circle | Manual |
| HOME-26 | Back to top | Scroll down past the hero, click the arrow at the bottom right | Button fades in after scrolling; page returns to the top | Yes (visibility) |
| HOME-27 | Coach cards | Run coach-profiles-demo.sql, open Train with the best | Three cards per row; square top panel shows photo or initials, sport chip and gold "N YEARS" badge; below: name, blue headline, bio (max three lines), up to three tags; without bio and headline the text is "<First name> coaches <sports> at Sportify Center." | Yes |
| HOME-28 | Coach rail | With four or more coaches click Next coaches and Previous coaches | Rail moves one coach (coaches 1-3, then 2-4); Previous is disabled at the start and Next at the end | Manual |
| HOME-29 | Private coach | Set coach_profile.is_public = 0 for one coach, reload Home | That coach is not shown; the others follow display_order | Yes (CoachDirectoryServiceImplTest) |
| HOME-30 | Long bio | Give a coach a bio longer than four lines | Card shows three lines with an ellipsis; clicking the name shows the full bio | Manual |
| HOME-31 | Specialty tags | Set specialties to "A, B; C, D" | Card shows tags A, B, C only; blank specialties show no tag row | Yes (homeCoachModel.test.ts) |
| HOME-32 | Hero video | Open Home in Chrome or Edge at 1280px or wider | Hero photo shows first, then muted 1080p basketball, football and tennis clips fade in and play one after another; headline fits on four lines, buttons are in the first screen, text stays readable | Yes (order) / Manual (look) |
| HOME-33 | Hero video pause | Click the round pause button at the bottom right of the hero, then click it again | Video pauses and the icon turns to play; second click resumes | Yes |
| HOME-34 | Hero video fallback | Open Home at 390px, or turn on reduced motion, or block assets.mixkit.co in DevTools | Only the hero photo shows; no pause button (blocking only the -1080.mp4 files still plays the 720p clips) | Yes |
| HOME-35 | Facilities from database | Start backend (V32 applied), open Where you play at 1440px and 390px | Subtitle reads 6 courts, 1 field and 1 pool; tiles Indoor Pool (1 pool), Basketball (2 courts), Badminton (2 courts), Tennis Court, Volleyball Court, Football Field; pool and football wide on desktop; phone: wide tiles full width | Yes |
| HOME-36 | Facility tile click | With the backend running, click the Badminton tile | Page moves to the class finder, Sport shows Badminton and has focus; Find classes keeps working | Yes |
| HOME-37 | Facility seed | Start the backend on a database at V31, then run SELECT code, name FROM facility | V32 applied once; 8 facilities including BD_COURT_1, BD_COURT_2, FB_FIELD, VB_COURT, TN_COURT; restarting adds no duplicates | Yes (CatalogSchemaIntegrationTest) |
| HOME-38 | Facility change shows on Home | In SSMS set is_active = 0 for Tennis Court, reload Home | Tennis tile disappears, grid switches to even three columns, subtitle becomes 5 courts, 1 field and 1 pool; set back to 1 to restore | Yes (model) / Manual |
| HOME-39 | Facility API down | Block /api/v1/facilities/public in DevTools, reload | Six photo tiles without counts and "Court details are temporarily unavailable." with Retry | Yes |
| HOME-40 | Public facility API | Invoke-RestMethod http://localhost:8080/api/v1/facilities/public without a token; then GET /api/v1/facilities without a token | First returns 200 with id, name, facilityType, sportId, capacity (no code); second returns 401 | Yes (PublicFacilityControllerTest) |
| HOME-41 | Vietnamese names | Coaches with names such as Lê Quốc Bảo, Phạm Hoàng Long, Võ Ngọc Trâm | All diacritics drawn clearly in the card heading, none clipped or overlapping; same in dark mode | Manual |
| HOME-42 | Font loading offline | Disconnect the internet, reload Home | Headings still use Be Vietnam Pro (bundled with the app, no Google Fonts request) | Manual |
| HOME-43 | Photo credits | On Home scroll to the footer, click Photo credits | /credits lists Photos, Demo coach portraits, Background videos and Font with license links; each source opens in a new tab; Back to Home returns to / | Yes |
