# Appllication Overview

A second memory to process and manage thoughts

## Feature roadmap

- Contains 2 nav items
  - Raw Thoughts
  - Daily View
- Record Thought
  - Accessible from an FAB on Raw thought screen or a homescreenwidget
  - Consits of a visualizer which animates when we speak and a textfield to show the transcribed text (Editable text field)
  - Option to save thought which will save the content in a json file in the daily folder with the current date as the name of the file
  - Json will have list wite each item contianing 3 field - timestamp, text, source
- Raw Thoughts
  - Contains a list of raw thoughts created throught the day which is stored in json file
  - Option to edit / delete thoughts
- Daily View
  - Shows list of dates and firsst few lines of the content
  - Basically a list of all files present in daily folder
  - Clicking on an item will open the file in markdown viewer
- Settings
  - Accessible from top right corner of all the screen
  - User has an option to sync with Google Drive (Basically sync the local files with GDrive folder files)
- Background features
  - Daily summary generation at night
  - Daily drive sync
