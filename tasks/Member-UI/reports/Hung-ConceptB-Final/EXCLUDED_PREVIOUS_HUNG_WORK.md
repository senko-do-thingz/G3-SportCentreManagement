# Excluded Previous Hung Work

## Coach redesign

The earlier Coach flow redesign was stopped at the user's direction after they said other contributors had completed it. This branch changes no Coach application pages, Coach API clients, or Coach authorization behavior.

## Commerce and profile redesign

The task split assigns the member commerce/account screen redesigns to Senko. This branch does not redesign SportPackagesPage, MyMembershipPage, MyProfilePage, member wallet, member payment, or booking form content. It only places the existing screens inside the Member shell and aligns navigation labels used by tests.

## Backend and data

No backend code, database schema, migrations, API contracts, notification records, or production data were changed. No sample notification data was added.

## Manual accessibility and live integration

Automated keyboard interaction and DOM tests are reported as automated checks only. No screen-reader pass is claimed. Screenshot captures use synthetic API responses and are not described as live integration tests.
