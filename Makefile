# Makefile

# Define default shell to be used
SHELL := /bin/bash

run-chrome-dev:
	google-chrome --incognito --new-window "http://127.0.0.1:4200" --remote-debugging-port=9222 --disable-web-security --user-data-dir="~/ChromeDevSession"

run-angular-client:
	cd frontend && npm start