/*global jQuery, dm4c*/

(function ($, dm4c) {

  // --- REST getter ------------------------------------------------

  function getRecipientTopics(mailId) {
    return dm4c.restc.get_related_topics(mailId, {
      assoc_type_uri: 'dm4.mail.recipient',
      my_role_type_uri: 'dm4.core.whole',
      others_role_type_uri: 'dm4.core.part'
    }).items
  }

  function getRecipientAssociation(mailId, recipientId) {
    return dm4c.restc.get_association('dm4.mail.recipient',
      mailId, recipientId, 'dm4.core.whole', 'dm4.core.part')
  }

  function getRecipientTypes() {
    return dm4c.restc.request('GET', '/mail/recipient/types').items
  }

  function associate(mailId, recipientId) {
    return dm4c.restc.request('GET', '/mail/' + mailId + '/recipient/' + recipientId)
  }

  function updateRecipientType(association, typeUri) {
    association.composite['dm4.mail.recipient.type'] = 'ref_uri:' + typeUri
    dm4c.restc.update_association(association)
  }

  // --- callbacks ---------------------------------------------------

  // associate recipient with selected mail and create an editor
  function onCompletionSelect($parent, item, $types) {
    var mailId = dm4c.selected_object.id
    associate(mailId, item.id)
    $parent.before(createRecipientEditor(mailId, item, $types))
    // @todo show but not focus the created association
  }

  // delete recipient association and remove parent editor
  function onRemoveButtonClick() {
    var association = $(this).parent().data('recipient').association
    dm4c.do_delete_association(association)
    $(this).parent().remove()
  }

  // update type of recipient association with selected value
  function onRecipientTypeSelect() {
    var association = $(this).parent().data('recipient').association
    $('option:selected', $(this)).each(function () {
      updateRecipientType(association, $(this).val())
    })
  }

  function onRecipientClick() {
    var recipient = $(this).parent().data('recipient').topic
    dm4c.show_topic(new Topic(recipient), 'show', null, true)
  }

  // --- jQuery factory methods --------------------------------------

  function createTypeSelector(types) {
    var $select = $('<select>').attr('size', 1)
    $.each(types, function (id, type) {
      $select.append($('<option>').val(type.uri).text(type.value))
    })
    return $select
  }

  function cloneAndSelectType($types, association) {
    return $types.clone().val(association.composite['dm4.mail.recipient.type'].uri)
  }

  function createAddButton($types, $recipients) {
    return $('<button>').text('add').click(function () {
      $recipients.append(dm4c.get_plugin('dm4.mail.plugin')
        .createCompletionField(function ($item, item) {
          onCompletionSelect($item, item, $types)
        }))
    })
  }

  function createRecipientEditor(mailId, recipient, $types) {
    var association = getRecipientAssociation(mailId, recipient.id),
      email = association.composite['dm4.contacts.email_address'].value,
      $email = $('<span>').text('<' + email + '>'),
      $remove = $('<button>').addClass('remove').text('Remove'),
      $link = dm4c.render.topic_link(recipient),
      $rTypes = cloneAndSelectType($types, association),
      $recipient = $('<div>').append($rTypes).append($link).append($email).append($remove)
    return $recipient.addClass('level2').data('recipient', {
      association: association,
      topic: recipient
    })
  }

  dm4c.add_simple_renderer('dm4.mail.recipient_renderer', {

    render_info: function (model, $parent) {
      var mail = model.toplevel_topic,
        recipients = getRecipientTopics(mail.id),
        $recipients = dm4c.render.topic_list(recipients)
      dm4c.render.field_label(model, $parent)
      $parent.append($recipients)
    },

    render_form: function (model, $parent) {
      var mail = model.toplevel_topic,
        recipients = getRecipientTopics(mail.id),
        types = dm4c.hash_by_id(getRecipientTypes()),
        $types = createTypeSelector(types),
        $recipients = $('<div>'),
        $add = createAddButton($types, $recipients)

      $.each(recipients, function (i, recipient) {
        var $recipient = createRecipientEditor(mail.id, recipient, $types)
        $recipients.append($recipient)
      })

      // register callbacks
      $recipients.on('click', 'button.remove', onRemoveButtonClick)
      $recipients.on('change', 'select', onRecipientTypeSelect)
      $recipients.on('click', 'a', onRecipientClick)

      // show time
      $parent.addClass('level1').append($recipients)
      $parent.after($('<div>').addClass('add-button').append($add))

      return function () {
        return true // set dummy field after edit
      }
    }
  })
}(jQuery, dm4c))
