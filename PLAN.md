## Plan: Next Steps Only

This file now tracks only remaining work. Completed phases are removed when finished. This keeps the plan focused on what’s left to do, without clutter from past steps.


## Backlog


2.  Add a screen for weekly summary
3. Add an option to customize summary prompt in settings
  1. Default prompt should also add a keyword sections
  2. The points should be short and concise
  3. Add a section for important keywords in the summary
4.  Redesign settings UI to have a Material 3 look and feel. Items must be categorized
5.  Gemini key must be in secured shared preference
6.  Add CICD automation for building and deploying the app
7.  Weekly files must use format YYYYWW.md
8.  Add option to download the markdown files
9.  Add link to google drive where data is stored
10. Publish on google play store
11. Add support for different LLM connections (including local models)
12. Conflict files must be handled in a separate directory data/conflicts and system must add a log that there was a conflict instead of creating it in the same directory where conflict happened
13. Add notification reminders to record thoughts at a specific time of the day - 9pm for example. This can be customizable in settings. Clicking opens record thought screen
14. Add notification at morning 9 to show summary of yesterday's thoughts. This can also be customizable in settings. Clicking opens the specific markdown file

---

## Future Work

- Search funtionality based on keywords
  - Text based search across all summaries
  - List will show date and a sentence containng that keyword with a highlight (Anything else that can be useful)
  - Tapping on the item will take you to the thought screen of that day and scroll to the thought containing that keyword and highlight it
- Add a chat view to ask questions about the data
