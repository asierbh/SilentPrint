# SilentPrint
Example app showcasing how to print documents from Android device __requesting to Google Print Cloud API__.
The main purpose is __avoid the dialog confirmation__ of PrintManager class (Android API level 21) to print without user confirmation.
## Requires:
- Add the project to [FireBase](https://firebase.google.com/).
- Include the `google-services.json` file into your App directory.
- Create oAuth id in google console and add `client_id` and `secret` into `strings.xml` fields.
- Add a local printer to [Google Print Cloud](https://www.google.com/cloudprint/learn/?hl=es).
