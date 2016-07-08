/*global jQuery, dm4c*/
(function ($, dm4c) {

    function getSender(childId, parentUri) {
        return dm4c.restc.get_topic_related_topics(childId, {
            assoc_type_uri: 'dm4.core.composition',
            my_role_type_uri: 'dm4.core.child',
            others_role_type_uri: 'dm4.core.parent',
            others_topic_type_uri: parentUri
        })[0]
    }

    function getSenderTopics(topicId) {
        return dm4c.restc.get_topic_related_topics(topicId, {
            assoc_type_uri: 'dm4.mail.sender',
            my_role_type_uri: 'dm4.core.parent',
            others_role_type_uri: 'dm4.core.child'
        })
    }

    function getSenderAssociation(topicId, senderId) {
        return dm4c.restc.get_association('dm4.mail.sender',
                topicId, senderId, 'dm4.core.parent', 'dm4.core.child', true)
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

        var email = association.childs['dm4.contacts.email_address']
        var $icon = dm4c.render.icon_link(sender, click)
        var $link = dm4c.render.topic_link(sender, click)

        $topic.empty().append($icon).append($link)
        if (email && email.value) {
            $topic.append($('<span>').text('<' + email.value + '>')).removeClass('invalidContact')
        } else {
            $topic.append($('<span>').text('<Unknown Email Address>')).addClass('invalidContact')
        }
        return $topic
    }

    dm4c.add_simple_renderer('dm4.mail.sender.renderer', {
        render_info: function (model, $parent) {
            var topic = model.parent.object
            var sender = getSenderTopics(topic.id)
            var $sender = dm4c.render.topic_list(sender)

            dm4c.render.field_label(model, $parent)
            $parent.append($sender)
        },
        render_form: function (model, $parent) {
            var topic = model.parent.object
            var $sender = $('<div>').addClass('sender box level1')

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
