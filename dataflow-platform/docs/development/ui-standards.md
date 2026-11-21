# UI standards

These rules bind every change to what a person sees in the Workspace. The reference is Databricks: calm, neutral, mature,
few words. A new screen should look as if it was always there. Use it with [Code standards](code-standards.md) and the
design scale in [Hardcoded values](literals.md#the-design-scale).

## Use what exists

Never rebuild one of these. If none fits, extend the closest one instead of adding a second way.

| You need | Use | Not |
|:---------|:----|:----|
| A page | `Page` (`wide` for 1440 px), then `PageHeader` with title, crumbs, actions, `Meta` | Your own container or `Title` |
| A section | `Panel` | A `Card` holding a heading |
| A button | `Action` with `tone`, `danger`, `loading`, `hint`, `shortcut` | Mantine `Button` |
| An icon button | `IconAction`; its `label` is both tooltip and screen-reader name | Mantine `ActionIcon` |
| A form or confirmation | `FormModal` with `submit`, `danger`, `loading` | A raw `Modal`, `window.confirm` |
| A dialog that only shows something | `ViewModal`, on `FormModal`'s widths | A raw `Modal` at its own width |
| A list of things | `Table`, or `DataGrid` for query results | Cards |
| A number in a table | `NumberCell` | A left-aligned `Td` |
| A status | `StatusBadge` or `StatusIcon`, labels in `lib/status.ts` | A coloured dot or your own word |
| A count beside a label or icon | `Count` with a `tone` | A styled `span` |
| First load | `TableSkeleton` for tables, `Loading` elsewhere | A centred spinner over a table |
| Nothing to show | `Empty` | A sentence in a `Text` |
| A failure on the page | `ErrorState`, or `Notice` for a warning | A raw `Alert` |
| A notification | `notifyDone`, `notifyWarning`, `notifyFailure(title)` from `components/notify` | `notifications.show` |
| An icon | `icons.tsx`, sized by `ICON`, `ICON_SMALL`, `ICON_LARGE` from `lib/icons` | The icon library directly |
| A colour | `COLOR`, `HAIRLINE`, `RADIUS` from `lib/colors`; `var(--df-*)` in CSS; neutral defaults in `theme.ts` | Hex, or `color="red"` |
| Spacing and type | Theme keys: `gap="snug"`, `size="sm"` | Raw pixels for font size, radius or a gap |
| A duration | `MOTION`, `EASE` from `lib/motion`; `POLL`, `FRESH`, `NOTICE` from `lib/timing` | `2000`, `'200ms'` |
| A link | `PAGE` and `links` from `lib/links` | `'/runs/' + id` |
| Data from the server | A hook in `api/hooks`, which uses `api/client.ts` | `fetch` in a component |
| A graph, log or editor that fills the page | `useRemainingHeight` from `components/layout` | A fixed pixel height |
| A page people can reach | An entry in `lib/navigation.ts` (sidebar, search and tab title) | Editing the sidebar, search or titles by hand |

## Every view has four states

Decide all four before writing the happy path:

| State | Show | Rule |
|:------|:-----|:-----|
| Loading | `TableSkeleton` or `Loading` | The skeleton keeps the final layout, so nothing jumps |
| Empty | `Empty` with a short title and, when there is a next step, one `Action` | `No saved queries` + `New query` |
| Failed | `ErrorState` | The header and the rest of the page stay usable; each page has an error boundary |
| Ready | The content | A refresh keeps rows in place, with no spinner flash |

## Layout

- Tables over cards. `StatCard` only for a few headline numbers.
- One table per view. A second one goes behind a tab or on its own page, not stacked underneath.
- Borders are `HAIRLINE`. Do not put a frame inside a frame.
- Labels left, numbers right, one baseline per row.
- Desktop only: no phone layouts, and no time spent checking them.

## Colour

- Neutral greys and one accent, `brand`. Colour means status or the primary action, never decoration.
- Layer tones (`TONES` in `lib/layers.ts`) only tell tables apart in graphs, the designer and search. Each keeps an
  OKLab distance (how different two colours look) of 0.09 or more from every status colour in both themes, so a gold
  table never reads as a warning.
- Status is always icon plus word; colour alone is not enough.
- Text has at least 4.5:1 contrast with its background and icons at least 3:1, in light and in dark.
  `lib/contrast.test.ts` holds the tokens to this.
- Every change is checked in both themes.

## Type

- Inter for text; JetBrains Mono for code, identifiers and values to copy.
- Sizes come from the scale; normal text is `sm` and page titles are `xl`.
- Weights are 400, 500 and 600. No capitals for emphasis.
- `df-num` gives figures equal width, so columns of numbers line up.

## Words

- As few as work. No sentence explaining a screen, no caption under a chart, no description under a setting: a good label
  replaces them. Details go in a `Hint` when someone asks for them.
- Sentence case everywhere: `Started by`, not `Started By`.
- A button is a verb, or a verb and a noun: `Rebuild`, `New pipeline`. Never `OK` or `Submit`.
- A failed action names what did not happen, and the message says why: title `Not saved`, then the server's message.
- An error says what is wrong and how to fix it, in plain words with no codes, no blame and no `Please`.
- Keep what the person typed when something fails.
- Use one word for one thing across the product (`run`, `pipeline`, `dataset`). No exclamation marks, no emoji.

## Actions

- At most one `tone="primary"` per page or dialog.
- A destructive action has `danger` and confirms in a `FormModal` that names what will go. If it cannot be undone and the
  loss is large, the person types the name, as `PipelineMenu` does for a full refresh.
- A disabled action says why through `hint`.
- While work runs, the action shows `loading` and the rest of the page keeps working. Nothing blocks the whole screen.
- Show a success notification only when the result is not already visible. Show a failure every time.

## Tables

- Column titles are one or two words. Long values truncate, with the full value in a `Hint`.
- One or two row actions are `IconAction`s; three or more go in a menu.
- Sorting, when a table has it, is controlled from the header.
- Keep tables out of cramped containers that force truncation.

## Forms

- Every input has a visible `label`. A placeholder is an example, never the label.
- An error appears at the field, in words, and suggests the fix when it is known.
- Enter submits; `FormModal` handles it. Offer a sensible default instead of a blank field.

## Waiting

| How long | Show |
|:---------|:-----|
| A moment | Nothing, or `loading` on the action |
| First load of a list or table | `TableSkeleton` |
| A few seconds, one thing | `loading` on that thing |
| Longer, such as a run | A status that updates through `POLL`, and the person can leave and come back |

## Keyboard and access

Target: [WCAG 2.2](https://www.w3.org/TR/WCAG22/) level AA, the accepted standard for accessible web pages.

- Everything works by keyboard. Use real buttons and links, never a clickable `div`.
- Focus is always visible and is never hidden behind a sticky bar. Do not remove the outline.
- After a route change the shell moves focus into the content unless something already holds it; a page that opens on
  something to type into (the SQL editor) focuses it. The first Tab offers Skip to content.
- A Tab stop does something. Graph edges are never Tab stops; graph nodes are only where they open something, and
  Enter or Space opens it. A widget with a cursor (tree, canvas) shows it while it has focus.
- One `main` per page (the shell's), one `h1` (`PageHeader`, or `PaneMain`'s `title` where the page has no header),
  and `Panel` titles are `h2`. Every field has a name; a placeholder is not one.
- Pointer targets are at least 24 × 24 px. `IconAction` size 20 is only for dense rows with 24 px between neighbours.
- Anything done by dragging also works without dragging.
- Updates that appear without focus moving use a role that screen readers announce, as `Notice` does with `role="alert"`.
  A `role="status"` region exists before it fills, and holds only what changes in steps, not a ticking counter.
- Motion goes through the tokens, which drop to zero when the person asks for reduced motion.

## Code in the UI

The React rules are in [Code standards](code-standards.md#by-language). For screens in particular:

- Pages compose; `components/` holds what is reused; `lib/` holds logic as pure, tested functions.
- Routes are lazy and preloaded when idle. Monaco stays in its own chunk.
- Polling and freshness use `POLL` and `FRESH`, and stop when nothing can change.

## Ten usability heuristics, applied

| Heuristic | Here |
|:----------|:-----|
| Show what the system is doing | The four states, `loading`, live run status |
| Speak the person's language | Plain words, no internal codes |
| Control and freedom | Cancel in every dialog, runs can be stopped, typed input survives failure |
| Consistency | The table at the top of this page |
| Prevent errors | Validation before saving, `danger` plus confirmation |
| Recognise rather than recall | Visible options, completion from the catalog, tooltips on icons |
| Flexibility | Keyboard shortcuts through `shortcut`, without crowding the screen |
| Minimal design | Few words, neutral colour, tables |
| Help recover from errors | Say what is wrong and how to fix it, near where it happened |
| Help and documentation | `Hint` when asked, `docs/` for depth |

## Before you commit

1. Does it use the blocks at the top of this page, and no new way of doing something that exists?
2. Are loading, empty, failed and ready all handled?
3. Is every word needed? Could a label replace a sentence?
4. Is colour used only for status and the primary action, with an icon and a word on status?
5. Does it work by keyboard, with visible focus and named icon buttons?
6. Does it look right in light and in dark?
7. Is it free of raw colours, sizes, durations and routes?

## What enforces this

ESLint and Stylelint fail the build on raw numbers, hex colours, palette colour names (`c="red"`), durations, font sizes
and radii, on raw routes, on `fetch` outside `api/client.ts`, on `notifications` outside `components/notify`, and on the
icon library or Mantine's buttons outside their wrappers. `lib/contrast.test.ts` checks
the colour tokens. The rest is review, using the list above.

## Sources

- [10 usability heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/) and [error message guidelines](https://www.nngroup.com/articles/error-message-guidelines/), Nielsen Norman Group.
- [WCAG 2.2](https://www.w3.org/TR/WCAG22/), W3C: contrast, focus, target size, dragging, status messages, error messages.
- [Data table usage](https://www.carbondesignsystem.com/components/data-table/usage/), [empty states](https://carbondesignsystem.com/patterns/empty-states-pattern/) and [loading](https://carbondesignsystem.com/patterns/loading-pattern/), IBM Carbon: skeletons, row actions, column titles, one next step.
- [You might not need an Effect](https://react.dev/learn/you-might-not-need-an-effect), React.
