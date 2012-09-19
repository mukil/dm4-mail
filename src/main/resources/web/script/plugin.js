dm4c.add_plugin('dm4.mail.plugin', function () {

  // --- REST ------------------------------------------------

  function autoComplete(term) {
    return dm4c.restc.request('GET', '/mail/autocomplete/' + term).items
  }

  function getSearchableParentTypes() {
    return dm4c.restc.request('GET', '/mail/search/parents').items
  }

  function reloadConfiguration() {
    dm4c.restc.request('GET', '/mail/config/load')
  }

  function copyMail() {
    var mail = dm4c.restc.request('POST', '/mail/' + dm4c.selected_object.id + '/copy')
    dm4c.show_topic(new Topic(mail), 'edit', null, true)
  }

  function saveAndSendMail() {
    dm4c.page_panel.save()
    sendMail()
  }

  function sendMail() {
    var mail = dm4c.restc.request('POST', '/mail/' + dm4c.selected_object.id + '/send')
    dm4c.show_topic(new Topic(mail), 'show', null, true)
  }

  // create a new mail with one recipient (the actual contact)
  function writeMail() {
    var mail = dm4c.restc.request('POST', '/mail/write/' + dm4c.selected_object.id)
    // TODO render recipient association
    dm4c.do_reveal_related_topic(mail.id)
    dm4c.show_topic(new Topic(mail), 'edit', null, true)
  }

  // configure menu and type commands
  dm4c.add_listener('topic_commands', function (topic) {
    if (!dm4c.has_create_permission('dm4.mail')) {
      return
    }
    var commands = [];
    if (topic.type_uri === 'dm4.mail') {
      commands.push({is_separator: true, context: "context-menu"})
      if (topic.composite['dm4.mail.date'] && topic.composite['dm4.mail.date'].value) {
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
      commands.push({is_separator: true, context: "context-menu"})
      commands.push({
        label: 'Reload',
        handler: reloadConfiguration,
        context: ['context-menu', 'detail-panel-show']
      })
    } else {
      $.each(getSearchableParentTypes(), function (r, type) {
        if (topic.type_uri === type.uri) {
          commands.push({is_separator: true, context: "context-menu"})
          commands.push({
            label: 'Write Mail',
            handler: writeMail,
            context: ['context-menu']
          })
        }
      })
    }
    return commands
  })

  // highlight all term occurrences in label with a <strong> element
  function highlightTerm(label, term) {
    var h = label
    $.each(term.split(' '), function (w, word) {
      h = h.replace(new RegExp('(' + word + ')', 'gi'), '<strong>$1</strong>')
    })
    return h
  }

  /**
   * Expose a auto-completion field creator.
   * onSelect gets $(this) and the attached item.
   */
  this.createCompletionField = function (label, onSelect) {
    var $elem = $('<input>').hide(),
      lastTerm = '', // save last request term
      $add = dm4c.ui.button(add, label).css('display', 'inline-block'),
      $cancel = dm4c.ui.button(cancel, 'Cancel').css('display', 'inline-block'),
      $div = $('<div>').addClass('add-button').append($elem).append($add).append($cancel)

    function add() {
      $cancel.show()
      $elem.show().focus()
      $add.hide()
    }

    function cancel() {
      $cancel.hide()
      $elem.hide()
      $add.show()
    }

    $elem.autocomplete({
      minLength: 1,
      source: function (request, response) {
        lastTerm = request.term
        response(autoComplete(request.term))
      },
      focus: function () {
        // prevent value inserted on focus
        return false;
      },
      select: function (event, ui) {
        onSelect($(this), ui.item)
        $(this).val('')
        cancel() // cancel after change to hide edit fields ;-)
        return false
      }
    })
    // render items with highlighted label and type specific icon
    $elem.data('autocomplete')._renderItem = function (ul, item) {
      var $img = dm4c.render.type_icon(item.type_uri).addClass('menu-icon'),
        $label = $('<span>').append(highlightTerm(item.value, lastTerm)),
        $a = $('<a>').append($img).append($label)
      $('<li>').data('item.autocomplete', item).append($a).appendTo(ul)
    }
    $cancel.hide() // hide after insert to prevent block style
    return $div.addClass('completion')
  }

})
