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

  function sendMail() {
    var mail = dm4c.restc.request('GET', '/mail/send/' + dm4c.selected_object.id)
    dm4c.show_topic(new Topic(mail), 'show', null, true)
  }

  // create a new mail with one recipient (the actual contact)
  function writeMail() {
    var mail = dm4c.restc.request('GET', '/mail/create/' + dm4c.selected_object.id)
    // @todo render recipient association
    dm4c.show_topic(new Topic(mail), 'edit', null, true)
  }

  // configure menu and type commands
  dm4c.add_listener('topic_commands', function (topic) {
    if (!dm4c.has_create_permission('dm4.mail')) {
        return
    }
    var commands = [];
    if (topic.type_uri === 'dm4.mail') {
      commands.push({
        label: 'Send',
        handler: sendMail,
        // @todo enable send command in edit mode (requires topic update and mode switch)
        context: ['context-menu', 'detail-panel-show']
      })
    } else if (topic.uri === 'dm4.mail.config') {
      commands.push({
        label: 'Reload',
        handler: reloadConfiguration,
        context: ['context-menu', 'detail-panel-show']
      })
    } else {
      $.each(getSearchableParentTypes(), function (r, type) {
        if (topic.type_uri === type.uri) {
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
      h = h.replace(word, '<strong>' + word + '</strong>')
    })
    return h
  }

  /**
   * Expose a auto-completion field creator.
   * onSelect gets $(this) and the attached item.
   */
  this.createCompletionField = function (onSelect) {
    var $elem = $('<input>'),
      lastTerm = '' // save last request term
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
    return $elem
  }

})
