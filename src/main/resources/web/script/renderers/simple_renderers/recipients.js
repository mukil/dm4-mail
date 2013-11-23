/*global jQuery, dm4c*/

(function ($, dm4c) {

  // --- REST getter ------------------------------------------------

  function getRecipient(childId, parentUri) {
    return dm4c.restc.get_topic_related_topics(childId, {
      assoc_type_uri: 'dm4.core.composition',
      my_role_type_uri: 'dm4.core.child',
      others_role_type_uri: 'dm4.core.parent',
      others_topic_type_uri: parentUri
    }).items[0]
  }

  function getRecipientTopics(mailId) {
    return dm4c.restc.get_topic_related_topics(mailId, {
      assoc_type_uri: 'dm4.mail.recipient',
      my_role_type_uri: 'dm4.core.parent',
      others_role_type_uri: 'dm4.core.child'
    }).items.sort(function (a, b) {
        return (a.value < b.value) ? -1 : (a.value > b.value) ? 1 : 0
    })
  }

  function getRecipientAssociations(mailId, recipientId) {
    var recipients = [],
      assocs = dm4c.restc.get_associations(mailId, recipientId, 'dm4.mail.recipient')
    $.each(assocs, function(a, assoc) {
        recipients.push(dm4c.restc.get_association_by_id(assoc.id, true))
    })
    return recipients
  }

  function getRecipientTypes() {
    return dm4c.restc.request('GET', '/mail/recipient/types').items
  }

  function associate(mailId, addressId) {
    return dm4c.restc.request('POST', '/mail/' + mailId + '/recipient/' + addressId)
  }

  // --- callbacks ---------------------------------------------------

  // delete recipient association and remove parent editor
  function onRemoveButtonClick() {
    var association = $(this).parent().parent().data('recipient').association
    dm4c.do_delete_association(association)
    $(this).parent().parent().remove()
  }

  // update type of recipient association with selected value
  function onRecipientTypeSelect() {
    var association = $(this).parent().data('recipient').association
    $('option:selected', $(this)).each(function () {
      association.composite['dm4.mail.recipient.type'] = 'ref_uri:' + $(this).val()
      dm4c.do_update_association(association)
    })
  }

  // --- jQuery factory methods --------------------------------------

  function createTypeSelector(types) {
    // TODO use render helper menu (core method needs a callback parameter)
    var $select = $('<select>').attr('size', 1)
    var $select = $('<select>').attr('size', 1)
    $.each(types, function (id, type) {
      $select.append($('<option>').val(type.uri).text(type.value))
    })
    return $select
  }

  function cloneAndSelectType($types, association) {
    return $types.clone().val(association.composite['dm4.mail.recipient.type'].uri)
  }

  function createRecipientEditor(recipient, association, $types) {
    function click(event) {
      event.preventDefault()
      dm4c.page_panel.save()
      dm4c.do_reveal_related_topic(recipient.id, 'show')
    }

    var email = association.composite['dm4.contacts.email_address'],
      $remove = dm4c.ui.button({ on_click: onRemoveButtonClick, icon: 'circle-minus', is_submit: false }),
      $icon = dm4c.render.icon_link(recipient, click),
      $link = dm4c.render.topic_link(recipient, click),
      $rTypes = cloneAndSelectType($types, association),
      $recipient = $('<div>').append($rTypes).append($icon).append($link)

    if (email && email.value) {
      $recipient.append($('<span>').text('<' + email.value + '>')).removeClass('invalidContact')
    } else {
      $recipient.append($('<span>').text('<Unknown Email Address>')).addClass('invalidContact')
    }

    $recipient.append($('<div>').addClass('remove-button').append($remove))
    return $recipient.addClass('recipient box level1').data('recipient', {
      association: association,
      topic: recipient
    })
  }

  function toggleWithFirstGet(label, onFirstShow) {
    var $get = dm4c.ui.button({ on_click: firstShow, label: label, is_submit: false }).css('display', 'inline-block'),
      $hide = dm4c.ui.button({ on_click: toggle, label: 'Hide', is_submit: false }).css('display', 'inline-block'),
      $show = dm4c.ui.button({ on_click: toggle, label: label, is_submit: false }).css('display', 'inline-block'),
      $buttons = $('<div>').addClass('add-button').append($get),
      $div = $('<div>')

    function firstShow() {
      onFirstShow($div)
      $get.remove()
      $buttons.append($show)
      $buttons.append($hide)
      $show.hide() // hide after insert to prevent block style
    }

    function toggle() {
      $show.toggle()
      $div.toggle()
      $hide.toggle()
    }

    return $('<div>').append($div).append($buttons)
  }


  function createRecipientListView(mailId) {
    return toggleWithFirstGet('Show', function ($parent) {
      $parent.append(dm4c.render.topic_list(getRecipientTopics(mailId)))
    })
  }

  function createRecipientEditorList(mailId, $parent) {
    var recipients = getRecipientTopics(mailId),
      types = dm4c.hash_by_id(getRecipientTypes()),
      $recipients = $('<div>'),
      $types = createTypeSelector(types),
      $add = dm4c.get_plugin('dm4.mail.plugin')
        .createCompletionField('Add', function ($item, item) {
          var association = associate(mailId, item.id),
            recipient = getRecipient(item.id, item.type_uri)
          $recipients.append(createRecipientEditor(recipient, association, $types))
          // TODO show but not focus the created association
        })
    $.each(recipients, function (i, recipient) {
      $.each(getRecipientAssociations(mailId, recipient.id), function(a, association) {
          $recipients.append(createRecipientEditor(recipient, association, $types))
      })
    })
    $recipients.on('change', 'select', onRecipientTypeSelect)
    return $('<div>').append($recipients).append($add)
  }

  dm4c.add_simple_renderer('dm4.mail.recipient.renderer', {

    render_info: function (model, $parent) {
      dm4c.render.field_label(model, $parent)
      var mail = model.toplevel_object,
        pluginResults = dm4c.fire_event('render_mail_recipients', mail)
      $.each(pluginResults, function (r, $info) {
        $parent.append($info)
      })
      if ($.isEmptyObject(pluginResults)) {
        $parent.append(dm4c.render.topic_list(getRecipientTopics(mail.id)))
      } else {
        //$parent.append(createRecipientListView(mail.id))
      }
    },

    render_form: function (model, $parent) {
      var mail = model.toplevel_object,
        pluginResults = dm4c.fire_event('render_mail_recipients')
      $.each(pluginResults, function (r, $info) {
        $parent.append($info)
      })
      if ($.isEmptyObject(pluginResults)) {
        $parent.append(createRecipientEditorList(mail.id))
      } else {
        //$parent.append(toggleWithFirstGet('Edit', function ($div) {
        //  $div.append(createRecipientEditorList(mail.id))
        //}))
      }

      return function () {
        return $.isEmptyObject(pluginResults) ? true : false // set dummy field after edit
      }
    }
  })
}(jQuery, dm4c))
