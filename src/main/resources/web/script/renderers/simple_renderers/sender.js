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
    function click() {
      dm4c.do_reveal_related_topic(sender.id)
    }
    var email = association.composite['dm4.contacts.email_address'].value,
      $email = $('<span>').text('<' + email + '>'),
      $icon = dm4c.render.icon_link(sender, click),
      $link = dm4c.render.topic_link(sender, click)
    return $topic.empty().append($icon).append($link).append($email)
  }

  dm4c.add_simple_renderer('dm4.mail.sender.renderer', {

    render_info: function (model, $parent) {
      var topic = model.toplevel_topic,
        sender = getSenderTopics(topic.id),
        $sender = dm4c.render.topic_list(sender)
      dm4c.render.field_label(model, $parent)
      $parent.append($sender)
    },

    render_form: function (model, $parent) {
      var topic = model.toplevel_topic,
        senderTopics = getSenderTopics(topic.id),
        $change = dm4c.ui.button(change, 'Change').css('display', 'inline-block'),
        $cancel = dm4c.ui.button(cancel, 'Cancel').css('display', 'inline-block'),
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

      function change() {
        $cancel.show()
        $search.show()
        $change.hide()
      }
      function cancel() {
        $cancel.hide()
        $search.hide()
        $change.show()
      }

      $.each(senderTopics, function (s, sender) { // only one sender is supported
        updateSenderView($topic, sender, getSenderAssociation(topic.id, sender.id))
      })

      // show time
      $parent.addClass('level1').append($sender.append($search.hide()))
      $parent.after($('<div>').addClass('add-button').append($change).append($cancel))
      $cancel.hide()

      return function () {
        return true // set dummy field after edit
      }
    }
  })
}(jQuery, dm4c))
