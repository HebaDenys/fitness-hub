# Localization staging

The Android resource locales currently shipped are the default English resources and Italian. Spanish remains part of FH-UX-05, not a completed locale in 0.3.3.

`backup_v2.es.xml` preserves the new Spanish backup translations for that work. It is NOT packaged as a runtime resource: registering Spanish for only these nine strings left the rest of the app untranslated and failed Android lint. Finish the full app resource coverage, including privacy and error messages, before moving the translations into `res/values-es`. Do not suppress MissingTranslation or mark translatable UI strings as non-translatable to bypass this requirement.
