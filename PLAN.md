## Plan: Next Steps Only

This file now tracks only remaining work. Completed phases are removed when finished. This keeps the plan focused on what’s left to do, without clutter from past steps.

## Backlog

1. Add notification when there is a conflict and ask user to review drive
2. Conflict files must be handled in a separate directory data/conflicts and system must add a log that there was a conflict instead of creating it in the same directory where conflict happened. Andd it should only ebe on drive
3. Remove same icons from mainn and sub setigs section
4. Add option to download the markdown files
5. Add notification reminders to record thoughts at a specific time of the day - 9pm for example. This can be customizable in settings. Clicking opens record thought screen
6. Add notification at morning 9 to show summary of yesterday's thoughts. This can also be customizable in settings. Clicking opens the specific markdown file
7.  Replace timestampMills to timestamp in json file
8.  Add option to switch between speech recognition and google coloud api (Handle no internet scenario as well when using google cloud api)
9.  Weekly view must have an option for custom prompt
10. Create a scheduled task for weekly summariazation

---

## Future Work

- Search funtionality based on keywords
  - Text based search across all summaries
  - List will show date and a sentence containng that keyword with a highlight (Anything else that can be useful)
  - Tapping on the item will take you to the thought screen of that day and scroll to the thought containing that keyword and highlight it
- Add a chat view to ask questions about the data
