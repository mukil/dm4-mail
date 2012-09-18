/*global jQuery, dm4c*/
(function ($, dm4c) {

  function getSenderTopics(topicId) {
    return dm4c.restc.get_topic_related_topics(topicId, {
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
    return dm4c.restc.request('POST', '/mail/' + topicId + '/sender/' + senderId)
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
        $topic = $('<div>'),
        $sender = $('<div>').append($topic)

      $.each(getSenderTopics(topic.id), function (s, sender) { // only one sender is supported
        updateSenderView($topic, sender, getSenderAssociation(topic.id, sender.id))
      })

      // show time
      $parent.append($sender).append(dm4c.get_plugin('dm4.mail.plugin')
        .createCompletionField('Change', function ($item, item) {
          updateSenderView($topic, item, changeSender(topic.id, item.id))
          // TODO show but not focus the created association
        }))

      return function () {
        return true // set dummy field after edit
      }
    }
  })
}(jQuery, dm4c))
