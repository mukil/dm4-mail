/*global jQuery, dm4c*/
(function ($, dm4c) {

  function getSenderTopics(topicId) {
    return dm4c.restc.get_related_topics(topicId, {
      assoc_type_uri: 'dm4.mail.sender',
      my_role_type_uri: 'dm4.core.whole',
      others_role_type_uri: 'dm4.core.part'
    }).items
  }

  function getSenderAssociation(topicId, senderId) {
    return dm4c.restc.get_association('dm4.mail.sender',
      topicId, senderId, 'dm4.core.whole', 'dm4.core.part')
  }

  function changeSender(topicId, senderId) {
    return dm4c.restc.request('GET', '/mail/' + topicId + '/sender/' + senderId)
  }

  function updateSenderView($topic, sender, association) {
    var email = association.composite['dm4.contacts.email_address'].value,
      $email = $('<span>').text('<' + email + '>'),
      $link = dm4c.render.topic_link(sender).click(function () {
        dm4c.show_topic(new Topic(sender), 'show', null, true)
      })
    return $topic.empty().append($link).append($email)
  }

  dm4c.add_field_renderer('dm4.mail.sender_renderer', {

    render_field: function (model, $parent) {
      var topic = model.toplevel_topic,
        sender = getSenderTopics(topic.id),
        $sender = dm4c.render.topic_list(sender)
      dm4c.render.field_label(model, $parent)
      $parent.append($sender)
    },

    render_form_element: function (model, $parent) {
      var topic = model.toplevel_topic,
        senderTopics = getSenderTopics(topic.id),
        $change = $('<button>').text('Change'),
        $cancel = $('<button>').text('Cancel').hide(),
        $topic = $('<div>'),
        $sender = $('<div>').append($topic),
        $search = dm4c.get_plugin('dm4.mail.plugin')
          .createCompletionField(function ($item, item) {
            // delete old sender and associate the chosen one
            $.each(senderTopics, function (s, sender) { // only one sender is supported
              dm4c.do_delete_association(getSenderAssociation(topic.id, sender.id))
            })
            // @todo show but not focus the created association
            updateSenderView($topic, item, changeSender(topic.id, item.id))
            $cancel.click() // cancel after change to hide edit fields ;-)
          })

      $change.click(function () {
        $cancel.show()
        $search.show()
        $change.hide()
      })
      $cancel.click(function () {
        $cancel.hide()
        $search.hide()
        $change.show()
      })

      $.each(senderTopics, function (s, sender) { // only one sender is supported
        updateSenderView($topic, sender, getSenderAssociation(topic.id, sender.id))
      })

      // show time
      $parent.addClass('level1').append($sender.append($search.hide()))
      $parent.after($('<div>').addClass('add-button').append($change).append($cancel))

      return function () {
        return true // set dummy field after edit
      }
    }
  })
}(jQuery, dm4c))
