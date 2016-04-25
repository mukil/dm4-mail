# DeepaMehta 4 Mail Plugin

SMTP Mail plugin

## Requirements

  * [DeepaMehta 4](http://github.com/jri/deepamehta) 4.7

## Usage

The plugin introduces the _Topic Type_ **Mail** and a **Mail Configuration** (with default settings) to your DeepaMehta 4 installation.

### Configure Global and Mail Preferences

_Edit_ the **Mail Configuration** _Topic_ associated with the "DeepaMehta 4 Mail" Plugin Topic.

There you can change the value for **Mail Transfer Agent Host** and choose a default *From* contact for mails send.

You can also change the default *Recipient Type* and the list of *Topic Types* used during autocompletion.

Note: After each change to the configuration you have to use the *Reload* command the Topic provides.

![configuration](https://github.com/mukil/dm4-mail/raw/master/doc/configuration.png)

### Configure a user specific sender through _Signatures_

Create a _Person_ Topic for yourself and assign an (at least one) _Email Address_ value to it.

Then create a _Signature_ Topic  and assign your _Person_  (contact) Topic to it.

Now reveal your *User Account* Topic and _Edit_ it. In the *From* field you can now assign your _Signature_ as the defaulf sender for all your _Mails_.

![user configuration](https://github.com/mukil/dm4-mail/raw/master/doc/userconfig.png)

### Create and Send a Mail

To write a mail to a specific _Person_ or _Institution_ use the *Write Mail* command in their context menu.

Additionally you can just _Create_ a new **Mail** and add _Recipients_ manually.

In both cases, like in any other mail program, you can edit the recipient list just through typing the names of your contacts, an autocompletion search helps you to do so.

Tips:

- You can use the `<TAB>` key to select an additional recipient from the result list
- `<ENTER>` submits the page and switches in the view mode

You can also have a look at the [Keyboard interaction](http://api.jqueryui.com/autocomplete/) section of the underlying jQuery UI component

![write mail](https://github.com/mukil/dm4-mail/raw/master/doc/mail.png)

### Release History

0.3.1, Apr 25, 2016

* No improvments usability or feature wise
* DeepaMehta 4.7 compatible: handles per-workspace file-iepositories

0.3.0-SNAPSHOT, Aug 28, 2015

* DeepaMehta 4.4.x compatible ..

0.2.1, Dec 07, 2014

* fixes: re-usage of aggregated e-mail address values
* fixes: "Send Again" feature (signatures and recipient types remain unique)
* fixes: a more robust autocomplete-function (NullPointer was thrown occasionally)
* DeepaMehta 4.3 compatible


Authors
-------
Danny Graf and Malte Reißig 2012-2016

