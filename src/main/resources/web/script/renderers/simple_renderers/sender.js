/*global jQuery, dm4c*/
(function ($, dm4c) {

  function getSender(childId, parentUri) {
    return dm4c.restc.get_topic_related_topics(childId, {
      assoc_type_uri: 'dm4.core.composition',
      my_role_type_uri: 'dm4.core.part',
      others_role_type_uri: 'dm4.core.whole',
      others_topic_type_uri: parentUri
    }).items[0]
  }

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

  function changeSender(topicId, addressId) {
    return dm4c.restc.request('POST', '/mail/' + topicId + '/sender/' + addressId)
  }

  function updateSenderView($topic, sender, association) {
    function click(event) {
      event.preventDefault()
      dm4c.page_panel.save()
      dm4c.do_reveal_related_topic(sender.id, 'show')
    }

    var email = association.composite['dm4.contacts.email_address'],
      $icon = dm4c.render.icon_link(sender, click),
      $link = dm4c.render.topic_link(sender, click)
    $topic.empty().append($icon).append($link)
    if (email) {
      $topic.append($('<span>').text('<' + email.value + '>')).removeClass('invalidContact')
    } else {
      $topic.append($('<span>').text('<NOT FOUND>')).addClass('invalidContact')
    }
    return $topic
  }

  dm4c.add_simple_renderer('dm4.mail.sender.renderer', {

    render_info: function (model, $parent) {
      var topic = model.toplevel_object,
        sender = getSenderTopics(topic.id),
        $sender = dm4c.render.topic_list(sender)
      dm4c.render.field_label(model, $parent)
      $parent.append($sender)
    },

    render_form: function (model, $parent) {
      var topic = model.toplevel_object,
        $sender = $('<div>').addClass('sender box level1')

      $.each(getSenderTopics(topic.id), function (s, sender) { // only one sender is supported
        updateSenderView($sender, sender, getSenderAssociation(topic.id, sender.id))
      })

      // show time
      $parent.append($sender).append(dm4c.get_plugin('dm4.mail.plugin')
        .createCompletionField('Choose', function ($item, item) {
          var sender = getSender(item.id, item.type_uri)
          updateSenderView($sender, sender, changeSender(topic.id, item.id))
          // TODO show but not focus the created association
        }))

      return function () {
        return true // set dummy field after edit
      }
    }
  })
}(jQuery, dm4c))
