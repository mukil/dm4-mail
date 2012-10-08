/*global jQuery, dm4c*/

(function ($, dm4c) {

  // --- REST getter ------------------------------------------------

  function getRecipientTopics(mailId) {
    return dm4c.restc.get_topic_related_topics(mailId, {
      assoc_type_uri: 'dm4.mail.recipient',
      my_role_type_uri: 'dm4.core.whole',
      others_role_type_uri: 'dm4.core.part'
    }).items.sort(function (a, b) {
        return (a.value < b.value) ? -1 : (a.value > b.value) ? 1 : 0
      })
  }

  function getRecipientAssociation(mailId, recipientId) {
    return dm4c.restc.get_association('dm4.mail.recipient',
      mailId, recipientId, 'dm4.core.whole', 'dm4.core.part')
  }

  function getRecipientTypes() {
    return dm4c.restc.request('GET', 'mail/recipient/types').items
  }

  function associate(mailId, recipientId) {
    return dm4c.restc.request('POST', 'mail/' + mailId + '/recipient/' + recipientId)
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

  function createRecipientEditor(mailId, recipient, $types) {
    function click(event) {
      event.preventDefault()
      dm4c.do_reveal_related_topic(recipient.id)
    }

    var association = getRecipientAssociation(mailId, recipient.id),
      $remove = dm4c.ui.button(onRemoveButtonClick, undefined, 'circle-minus'),
      $icon = dm4c.render.icon_link(recipient, click),
      $link = dm4c.render.topic_link(recipient, click),
      $rTypes = cloneAndSelectType($types, association),
      $recipient = $('<div>').append($rTypes).append($icon).append($link)

    $.each(association.composite['dm4.contacts.email_address'], function (e, email) {
      $recipient.append($('<span>').text('<' + email.value + '> '))
    })
    $recipient.append($('<div>').addClass('remove-button').append($remove))
    return $recipient.addClass('box level1').data('recipient', {
      association: association,
      topic: recipient
    })
  }

  function toogleWithFirstGet(label, onFirstShow) {
    var $get = dm4c.ui.button(firstShow, label).css('display', 'inline-block'),
      $hide = dm4c.ui.button(toggle, 'Hide').css('display', 'inline-block'),
      $show = dm4c.ui.button(toggle, label).css('display', 'inline-block'),
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
    return toogleWithFirstGet('Show', function ($parent) {
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
          associate(mailId, item.id)
          $recipients.append(createRecipientEditor(mailId, item, $types))
          // TODO show but not focus the created association
        })
    $.each(recipients, function (i, recipient) {
      $recipients.append(createRecipientEditor(mailId, recipient, $types))
    })
    $recipients.on('change', 'select', onRecipientTypeSelect)
    return $('<div>').append($recipients).append($add)
  }

  dm4c.add_simple_renderer('dm4.mail.recipient.renderer', {

    render_info: function (model, $parent) {
      dm4c.render.field_label(model, $parent)
      var mail = model.toplevel_topic,
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
      var mail = model.toplevel_topic,
        pluginResults = dm4c.fire_event('render_mail_recipients')
      $.each(pluginResults, function (r, $info) {
        $parent.append($info)
      })
      if ($.isEmptyObject(pluginResults)) {
        $parent.append(createRecipientEditorList(mail.id))
      } else {
        //$parent.append(toogleWithFirstGet('Edit', function ($div) {
        //  $div.append(createRecipientEditorList(mail.id))
        //}))
      }

      return function () {
        return $.isEmptyObject(pluginResults) ? true : false // set dummy field after edit
      }
    }
  })
}(jQuery, dm4c))
