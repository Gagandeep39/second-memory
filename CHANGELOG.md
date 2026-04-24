# Changelog

All notable changes to this project will be documented in this file.

## 8.0

- Add support for multiple LLM connections (including local models)
- Improved empty list message
- Improvised default summary prompt
- Fixed Google drive link
- Increased log limit
- Improved settings page message (Remove special emoji characters)
- Added Weekly view
- Added a weekly job for usnday 18:00 (Summarize data from Monday of Sunday of same week)
- Added Sync fixes
- Conflic files now stored in `data/conflict` folder only on drive
- Added a notification when there is a conflict
- Added notification toogle in settings 
- Added a fix to resolve infite syncing issue(Clears flag on startup)
- Daily and weekly sumary job now use background worker
- Improved App widget (Now has a App launch button)
- Fixed blank Record screen icon on homescreen

## 7.0

- Add an option to customize summary prompt in settings
- Add CICD automation for building and deploying the app
- Key must be in secured shared preference
- Publish on google play store
- Add support for different LLM connections (including local models)

## 6.0

- Fixed Sync now issue (Removed deprecated credential logic in google drive call)
- Changed target SDK to 37

## 5.0

- Progaurd rules fixes

## 4.0

- Implemented CICD releases (Requires pushing to develop branch)
- Enabled minify for smaller build

## 3.0

- No Change

## 2.0 

- Initial release using Pipeline

## 1.0

- Initial release of the app.
