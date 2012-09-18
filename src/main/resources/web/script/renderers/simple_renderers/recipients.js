/*global jQuery, dm4c*/

(function ($, dm4c) {

  // --- REST getter ------------------------------------------------

  function getRecipientTopics(mailId) {
    return dm4c.restc.get_topic_related_topics(mailId, {
      assoc_type_uri: 'dm4.mail.recipient',
      my_role_type_uri: 'dm4.core.whole',
      others_role_type_uri: 'dm4.core.part'
    }).items.sort(function (a, b) {
        return (a.value < b.value) ? -1 : (a.value > b.value) ? 1 : 0;
      })
  }

  function getRecipientAssociation(mailId, recipientId) {
    return dm4c.restc.get_association('dm4.mail.recipient',
      mailId, recipientId, 'dm4.core.whole', 'dm4.core.part')
  }

  function getRecipientTypes() {
    return dm4c.restc.request('GET', '/mail/recipient/types').items
  }

  function associate(mailId, recipientId) {
    return dm4c.restc.request('POST', '/mail/' + mailId + '/recipient/' + recipientId)
  }

  // --- REST update ------------------------------------------------

  function updateRecipientType(association, typeUri) {
    association.composite['dm4.mail.recipient.type'] = 'ref_uri:' + typeUri
    // TODO use and test webclient update!
    dm4c.do_update_association(association)
    //dm4c.restc.update_association(association)
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
      updateRecipientType(association, $(this).val())
    })
  }

  // --- jQuery factory methods --------------------------------------

  function createTypeSelector(types) {
    // TODO use render helper menu (core method needs a callback parameter)
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
    function click() {
      dm4c.do_reveal_related_topic(recipient.id)
    }

    var association = getRecipientAssociation(mailId, recipient.id),
      email = association.composite['dm4.contacts.email_address'].value,
      $email = $('<span>').text('<' + email + '>'),
      $remove = dm4c.ui.button(onRemoveButtonClick, undefined, 'circle-minus'),
      $icon = dm4c.render.icon_link(recipient, click),
      $link = dm4c.render.topic_link(recipient, click),
      $rTypes = cloneAndSelectType($types, association),
      $recipient = $('<div>').append($rTypes).append($icon).append($link).append($email)
    $recipient.append($('<div>').addClass('remove-button').append($remove))
    return $recipient.addClass('box level1').data('recipient', {
      association: association,
      topic: recipient
    })
  }

  dm4c.add_simple_renderer('dm4.mail.recipient.renderer', {

    render_info: function (model, $parent) {
      var mail = model.toplevel_topic,
        recipients = getRecipientTopics(mail.id),
        $recipients = dm4c.render.topic_list(recipients)
      dm4c.render.field_label(model, $parent)
      $parent.append($recipients)
    },

    render_form: function (model, $parent) {
      var mail = model.toplevel_topic,
        types = dm4c.hash_by_id(getRecipientTypes()),
        recipients = getRecipientTopics(mail.id),
        $types = createTypeSelector(types),
        $recipients = $('<div>')
      $.each(recipients, function (i, recipient) {
        $recipients.append(createRecipientEditor(mail.id, recipient, $types))
      })

      // register select callback
      $recipients.on('change', 'select', onRecipientTypeSelect)

      // show time
      $parent.append($recipients).append(dm4c.get_plugin('dm4.mail.plugin')
        .createCompletionField('Add', function ($item, item) {
          // associate recipient with selected mail and create an editor
          associate(mail.id, item.id)
          $recipients.append(createRecipientEditor(mail.id, item, $types))
          // TODO show but not focus the created association
        }))

      return function () {
        return true // set dummy field after edit
      }
    }
  })
}(jQuery, dm4c))
