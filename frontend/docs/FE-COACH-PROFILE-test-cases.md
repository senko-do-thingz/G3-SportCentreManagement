# FE-COACH-PROFILE - Manager edits coach profiles and uploads photos - Test cases

| ID | Area | Steps | Expected | Automated |
|---|---|---|---|---|
| CP-01 | Action visibility | Log in as Manager, open User Management | Coach rows show Edit coach profile; member, receptionist and manager rows do not | Yes |
| CP-02 | Load profile | Click Edit coach profile on a coach | Dialog shows the current photo (or initials) with Upload photo, headline, bio, years, specialties, display order and Show on the Home page from the database | Yes |
| CP-03 | Upload photo | Click Upload photo, choose a JPEG portrait | Preview shows the new photo, message Photo uploaded and saved, button becomes Change photo; Network shows one POST .../coach-profile/photo with a 640 x 640 JPEG | Yes |
| CP-04 | Client validation | Choose a GIF; then enter 61 years and click Save profile | Choose a JPEG, PNG or WebP photo. shown and nothing uploaded; years error shown and no PUT sent | Yes |
| CP-05 | Save | Change headline, years 7, specialties, order 2, untick Show on the Home page, Save | One PUT with trimmed text; toast Coach profile updated; dialog closes | Yes |
| CP-06 | Home reflects change | After CP-05 open Home and reload | Coach hidden from Home; tick again and the new photo, headline and tags appear in order | Manual |
| CP-07 | No-op save | Open and save without changes | 200, no new Activity log row | Yes (service test) |
| CP-08 | Activity log | Save a change, open Activity log | One USER_UPDATED row "Coach profile updated" by the manager | Yes (service test) / Manual |
| CP-09 | Server validation | PUT with avatarUrl javascript:alert(1), yearsExperience -1, displayOrder 1000; POST photo with a renamed text file and with a 3 MB file | 400 with details for each field; text file 400 Photo must be a JPEG, PNG or WebP image; 3 MB file 413 File must be at most 2 MB | Yes (controller and service tests) |
| CP-10 | Access | Call GET/PUT as Coach, and without a token | 403 for Coach, 401 without token | Yes (controller test) |
| CP-11 | Unknown coach | GET /api/v1/manager/users/999/coach-profile | 404 Coach profile not found; dialog shows error with Try again | Yes |
| CP-12 | Photo on Home | After CP-03 open Home and reload | The coach card shows the uploaded photo, cropped square | Manual |
| CP-13 | Remove photo | Click Remove photo, then Save profile | Card and Home show initials; SELECT COUNT(*) FROM user_avatar WHERE user_id = <id> returns 0 | Yes (service test) / Manual |
| CP-14 | Public photo URL | Open http://localhost:8080/api/v1/users/<id>/avatar without logging in | Image returned with Cache-Control max-age=31536000, public; unknown id returns 404 | Yes (UserAvatarControllerTest) |
| CP-15 | Migration | Start the backend on a database at V32 | V33 applied once; table user_avatar exists | Manual |
| CP-16 | Bundled demo photos | Run download-coach-photos.ps1, then 02-coach-photos.sql, reload Home with the network cable unplugged | Six coach photos load from /images/coaches/*.jpg; Edit coach profile shows the same photo and saves without a validation error | Yes (path rules) / Manual |
