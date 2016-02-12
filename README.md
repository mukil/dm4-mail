# DeepaMehta 4 Mail Plugin

SMTP Mail plugin

## Requirements

  * [DeepaMehta 4](http://github.com/jri/deepamehta) 4.7

## Usage

the plugin adds a *Mail* topic type and a *Mail Configuration* with default settings.

### configure global and default mail preferences

edit the configuration associated with the mail plugin
to change the *Mail Transfer Agent Host* and choose a default *From* contact

you can also change the default *Recipient Type* and
the base *Topic Type* list of autocompletion

hint: after each configuration update you have to call the *Reload* action of it

![configuration](https://github.com/mukil/dm4-mail/raw/master/doc/configuration.png)

### configure user specific sender and signature

create a contact with at least one email address and a signature assigned to it

reveal your *User Account* and edit it to assign a *From* contact

from now on each new message is assigned to the configured sender and the signature
of the corresponding email address

![user configuration](https://github.com/mukil/dm4-mail/raw/master/doc/userconfig.png)

### create and send a mail

to write a mail to a specific contact use the *Write Mail* action of the context menu

or use the *Mail* entry of the *Create* menu to create a new mail without recipients

in both cases, like in any other mail client,
you can edit the recipient list with an autocompletion search

keyboard hints: use the `<TAB>` key to select an additional recipient from the result list
`<ENTER>` submits the page and switches in the view mode, have a look at the
[Keyboard interaction](http://api.jqueryui.com/autocomplete/) section
of the underlying jQuery UI component

![write mail](https://github.com/mukil/dm4-mail/raw/master/doc/mail.png)

### Release History

0.3.1, UPCOMING

* DeepaMehta 4.7 compatible

0.3.0-SNAPSHOT, Aug 28, 2015

* DeepaMehta 4.4.x compatible ..

0.2.1, Dec 07, 2014

* fixes: re-usage of aggregated e-mail address values
* fixes: "Send Again" feature (signatures and recipient types remain unique)
* fixes: a more robust autocomplete-function (NullPointer was thrown occasionally)
* DeepaMehta 4.3 compatible


Authors
-------
Danny Graf, Malte Reißg 2012-2015

