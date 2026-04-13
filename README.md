# Appllication Overview

A second memory to process and manage thoughts

## Feature roadmap

- Contains 2 nav items
  - Raw Thoughts
  - Daily View
- Record Thought
  - Accessible from an FAB on Raw thought screen or a homescreenwidget
- Raw Thoughts
  - Contains a list of raw thoughts created throught the day which is stored in sqlite file
  - Option to edit / delete thoughts
- Daily View
  - Shows list of dates and firsst few lines of the content
  - Basically a list of all files present in daily folder
  - Clicking on an item will open the file in markdown viewer
- Settings
  - Accessible from top right corner of all the screen
  - It is where user can provide their git repo url and token
  - Option to clone repo (if not already cloned else show below options) and pull latest changes
  - Option to sync with Git repo pull and then push (During conflicts only option to pull / push)
  - Option to do it aumatically everyday in the background
- Background features
  - Daily summary generation at night
  - Daily git sync


## Reasoning

- Easier to edit modify things across platform
- Easier to get infromation using an LLM as its plain mardown text