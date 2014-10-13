# DeepaMehta 4 Mail Plugin

SMTP Mail plugin

## Requirements

  * [DeepaMehta 4](http://github.com/jri/deepamehta) 4.1.3-SNAPSHOT

## Usage

the plugin adds a *Mail* topic type and a *Mail Configuration* with default settings.

### configure global and default mail preferences

edit the configuration associated with the mail plugin
to change the *Mail Transfer Agent Host* and choose a default *From* contact

you can also change the default *Recipient Type* and
the base *Topic Type* list of autocompletion

hint: after each configuration update you have to call the *Reload* action of it

![configuration](https://github.com/dgf/dm4-mail/raw/master/doc/configuration.png)

### configure user specific sender and signature

create a contact with at least one email address and a signature assigned to it

reveal your *User Account* and edit it to assign a *From* contact

from now on each new message is assigned to the configured sender and the signature
of the corresponding email address

![user configuration](https://github.com/dgf/dm4-mail/raw/master/doc/userconfig.png)

### create and send a mail

to write a mail to a specific contact use the *Write Mail* action of the context menu

or use the *Mail* entry of the *Create* menu to create a new mail without recipients

in both cases, like in any other mail client,
you can edit the recipient list with an autocompletion search

keyboard hints: use the `<TAB>` key to select an additional recipient from the result list
`<ENTER>` submits the page and switches in the view mode, have a look at the
[Keyboard interaction](http://api.jqueryui.com/autocomplete/) section
of the underlying jQuery UI component

![write mail](https://github.com/dgf/dm4-mail/raw/master/doc/mail.png)


