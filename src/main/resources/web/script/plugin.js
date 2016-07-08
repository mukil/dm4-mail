dm4c.add_plugin('dm4.mail.plugin', function () {

    function autoComplete(term) {
        return dm4c.restc.request('GET', '/mail/autocomplete/' + term)
    }

    function getSearchableParentTypes() {
        return dm4c.restc.request('GET', '/mail/search/parents')
    }

    function reloadConfiguration() {
        dm4c.restc.request('GET', '/mail/config/load')
    }

    function copyMail() {
        var pluginResults = dm4c.fire_event('copy_mail')
        if ($.isEmptyObject(pluginResults)) { // copy it
            var mail = dm4c.restc.request('POST', '/mail/' + dm4c.selected_object.id + '/copy?recipients=true')
            dm4c.show_topic(new Topic(mail), 'edit', null, true)
        } else { // plugin copied it before
            dm4c.show_topic(new Topic(pluginResults[0]), 'show', null, true)
            dm4c.fire_event('show_mail')
            dm4c.show_topic(new Topic(pluginResults[0]), 'edit', null, true)
        }
    }

    function saveAndSendMail() {
        dm4c.page_panel.save()
        sendMail()
    }

    function showStatusReport(status) {
        var $message = $('<span>').text(status.message),
                dialogConfiguration = {
                    id: 'mailStatusReport',
                    title: 'Status Report',
                    content: $('<div>').append($message)
                }
        if (status.success) {
            $message.addClass('success')
            dialogConfiguration.button_label = 'Ok'
            dialogConfiguration.button_handler = closeAfterSuccess
        } else {
            $message.addClass('error')
            var $errors = $('<ul>')
            for (var e in status.errors) {
                var error = status.errors[e]
                // TODO: for (var t in errors.topics)
                // var $topics = $('<ul>')
                // $topics.append($('<li>').text(topic))
                $errors.append($('<li>').append($('<span>').text(error.message)).append('<br/>')
                        .append($('<span>').text(error.topics)))
            }
            dialogConfiguration.content.append($errors)
        }

        var dialog = dm4c.ui.dialog(dialogConfiguration) // opens dialog automatically
        function closeAfterSuccess() {
            dm4c.do_select_topic(status.topic_id) // show mail with sent date and ID
            dialog.close(217)
        }
    }

    function sendMail() {
        var pluginResults = dm4c.fire_event('send_mail')
        if ($.isEmptyObject(pluginResults)) { // send it
            showStatusReport(dm4c.restc.request('POST', '/mail/' + dm4c.selected_object.id + '/send'))
        } else { // plugin sends it before
            showStatusReport(pluginResults[0])
        }
    }

    // create a new mail with one recipient (the actual contact)
    function writeMail() {
        var mail = dm4c.restc.request('POST', '/mail/write/' + dm4c.selected_object.id)
        // TODO render recipient association
        dm4c.do_reveal_related_topic(mail.id, 'show')
        dm4c.show_topic(new Topic(mail), 'edit', null, true)
    }

    // configure menu and type commands
    dm4c.add_listener('topic_commands', function (topic) {
        // Note: create permission now managed by core
        var commands = []
        if (topic.type_uri === 'dm4.mail') {
            commands.push({is_separator: true, context: 'context-menu'})
            if (topic.childs['dm4.mail.date'] && topic.childs['dm4.mail.date'].value) {
                commands.push({
                    label: 'Send Again',
                    handler: copyMail,
                    context: ['context-menu', 'detail-panel-show']
                })
            } else {
                commands.push({
                    label: 'Send',
                    handler: sendMail,
                    context: ['context-menu', 'detail-panel-show']
                })
                commands.push({
                    label: 'Send',
                    handler: saveAndSendMail,
                    context: ['detail-panel-edit']
                })
            }
        } else if (topic.uri === 'dm4.mail.config') {
            commands.push({is_separator: true, context: 'context-menu'})
            commands.push({
                label: 'Reload',
                handler: reloadConfiguration,
                context: ['context-menu', 'detail-panel-show']
            })
        } else {
            $.each(getSearchableParentTypes(), function (r, type) {
                if (topic.type_uri === type.uri) {
                    commands.push({is_separator: true, context: 'context-menu'})
                    commands.push({
                        label: 'Write Mail',
                        handler: writeMail,
                        context: ['context-menu', 'detail-panel-show']
                    })
                }
            })
        }
        return commands
    })

    // highlight all term occurrences in label with a <strong> element
    function highlightTerm(label, term) {
        var h = label.trim()
        $.each(term.trim().split(' '), function (w, word) {
            h = h.replace(new RegExp('(' + word + ')', 'gi'), '<strong>$1</strong>')
        })
        return h
    }

    /**
     * Expose a auto-completion field creator.
     * onSelect gets $(this) and the attached item.
     */
    this.createCompletionField = function (label, onSelect) {
        var $elem = $('<input>').hide().blur(cancel),
                lastTerm = '', // save last request term
                $add = dm4c.ui.button({on_click: add, label: label, is_submit: false}).css('display', 'inline-block'),
                $cancel = dm4c.ui.button({on_click: cancel, label: 'Cancel', is_submit: false}).css('display', 'inline-block'),
                $div = $('<div>').addClass('add-button').append($elem).append($add).append($cancel)

        function add() {
            $cancel.show()
            $elem.val('') // overwrite the old search term
            $elem.show()
            $elem.focus() // FIXME - this focus call submits the page on <enter>
            $add.hide()
        }

        function cancel() {
            $cancel.hide()
            $elem.hide()
            $add.show()
        }

        // configure the autocomplete plugin
        $elem.autocomplete({
            minLength: 2,
            source: function (request, response) {
                lastTerm = request.term
                response(autoComplete(request.term))
            },
            focus: function (event) {
                // prevent value inserted on focus
                event.preventDefault()
            },
            select: function (event, ui) {
                // prevent value inserted after selection
                event.preventDefault()

                onSelect($(this), ui.item)
                cancel(event) // cancel after change to hide edit fields ;-)
            }
        })

        // render items with highlighted label and type specific icon
        $elem.data('ui-autocomplete')._renderItem = function (ul, item) {
            var $img = dm4c.render.type_icon(item.type_uri).addClass('menu-icon'),
                    $label = $('<span>').append(highlightTerm(item.value, lastTerm)),
                    $a = $('<a>').append($img).append($label)
            return $('<li>').data('item.autocomplete', item).append($a).appendTo(ul)
        }
        $cancel.hide() // hide after insert to prevent block style
        return $div.addClass('completion')
    }

})
