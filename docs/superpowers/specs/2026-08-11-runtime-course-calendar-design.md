# Pulling the course calendar from dhamma.org at runtime

**Status:** design only. Nothing in the app fetches a schedule yet.
**Date:** 2026-08-11.

## The idea

Centre schedules are public and canonical. Dhamma Sudha's lives at
`https://www.dhamma.org/en/schedules/schsudha`, and every centre follows the
same `/en/schedules/sch<subdomain>` shape. Today the app ships a hand-made
`seed/courses-sudha-2026-2027.sql`, which means a tablet is right for exactly
one centre and goes stale the moment the centre publishes next year.

If the appliance could read that page, an install would be "pick your centre"
instead of "someone transcribes 39 dates into SQL", and the calendar would stay
current on its own.

## Why this is not just "parse the page"

The schedule pages are HTML written for humans. Three things follow:

1. **The markup is not a contract.** A layout change on dhamma.org would break
   parsing silently. Whatever ships must treat a parse failure as "keep what we
   have", never "the centre has no courses" — an empty calendar means an
   appliance that rings nothing, which is worse than a stale one.
2. **A wrong date is worse than a missing one.** A misparsed course start moves
   day 0, and every gong with it. Anything imported must be *reviewed* before it
   fires: import into a staging list, show staff what changed, let them accept.
   Never write straight into `courses`.
3. **The network is not on the critical path and must stay off it.** Fetching
   belongs where the doha download already lives — optional, fire-and-forget,
   incapable of delaying a gong.

## What already exists

- `tools/export_centres_json.py` turns the public directory HTML into
  `assets/seed/centres.json`: `subdomain`, `name`, `place`, `region`,
  `schedule` path, and whether it is a full centre. Run it against
  `https://www.dhamma.org/en/locations/directory`.
- `tools/export_courses_json.py` turns a centre's `.sql` calendar into the
  asset the app seeds from, with duplicate-date and date-validity checks.
- `SeedLoader.applyCourses` already installs a calendar exactly once, refuses to
  land on top of hand-entered courses, and drops rows naming an unknown course
  type.

## Proposed shape when this is built

1. **Setup gains "Centre".** A searchable list from `centres.json`; the choice
   is stored as a `centre_subdomain` setting. This alone is worth shipping — it
   makes the bundled calendar's provenance explicit instead of implied.
2. **`CentreScheduleFetcher`** (in `net/`, alongside `NetworkProbe`): fetches
   the schedule page over the existing `HttpURLConnection` pattern — no new
   dependency — and returns raw HTML. No parsing.
3. **Pure `domain/CourseCalendarParser`**: HTML string → `List<SeedCourse>` +
   a list of rows it could not understand. Pure so the fixtures for each centre
   layout are JVM tests, which is the only way this stays honest.
4. **Review, then apply.** Courses gains an "Update from dhamma.org" action
   showing added / removed / unchanged counts, with the same two-tap confirm
   the backup restore uses. Applying reuses `applyCourses`-style guards.
5. **Never automatic.** No background sync. A calendar that changes itself
   overnight is a schedule nobody can reason about the morning it goes wrong.

## Course-type mapping

The public pages say "10-Day", "Satipatthana", "3-Day", "Teen" in prose. The
app needs `course_type_id`. That mapping is a small pure lookup with an explicit
"unknown → skip and report" branch; it must never guess, because guessing a type
picks the wrong daily pattern and the wrong doha.

## Open question for the owner

Is a tablet ever pointed at a **non-centre** (a gypsy-course venue)? Those run
courses but have no permanent site, and their schedule pages use
`/en/schedules/noncenter/<sub>`. The extractor keeps them and flags them; the
answer decides whether the centre picker filters them out.
