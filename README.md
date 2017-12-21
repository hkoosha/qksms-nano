# qksms-nano
Extremely stripped down version of moezbhatti/qksms plus message blocking by 
word patterns, numner prefix and more.

## Important!!

This project has a fatal bug on android 8 (and probably 7): it does not ask for 
required permissions, and simply crashes. (and you wont even get a 'app crached'
message).

You have to go to settings -> apps -> permissions, and manually give all the
permissions this app requires. That is, read contacts, read/send sms, and
call log.
