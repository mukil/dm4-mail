/*global jQuery, dm4c*/
(function ($, dm4c) {

  function getAllSignatures() {
    return dm4c.restc.get_topics('dm4.mail.signature', false, true).items
  }

  function getSignature(signatureId) {
    return dm4c.restc.get_topic_by_id(signatureId, true)
  }

  function getSignatureBody(signatureId) {
    var signature = getSignature(signatureId)
    if (signature.composite['dm4.mail.body']) {
      return signature.composite['dm4.mail.body'].value || 'empty signature'
    } else {
      return '' // invalid signature
    }
  }

  function getSignatureOfMail(mailId) {
    var signature = dm4c.restc.get_topic_related_topics(mailId, {
      assoc_type_uri: 'dm4.core.aggregation',
      my_role_type_uri: 'dm4.core.whole',
      others_role_type_uri: 'dm4.core.part',
      others_topic_type_uri: 'dm4.mail.signature'
    }, false, 1)
    if (signature.total_count === 1) {
        return signature.items[0].id // the first one
    } else {
        return -1
    }
  }

  function createSignatureMenu(selected, onChoose) {
    var menu = dm4c.ui.menu(onChoose)
    $.each(getAllSignatures(), function (t, topic) {
      menu.add_item({ label: topic.value, value: topic.id })
    })
    if (selected !== -1) {
      menu.select(selected)
    }
    return menu
  }

  dm4c.add_multi_renderer('dm4.mail.signature.renderer', {

    render_info: function (pages, $parent, level) {
      dm4c.render.field_label(pages[0].object_type.value, $parent)
      if (pages[0].object.id !== -1) { // ignore empty default page model
        $parent.append($('<div>').append(getSignatureBody(pages[0].object.id)))
      }
    },

    render_form: function (pages, $parent, level) {
      var deselectedId = -1, $signature = $('<div>'),
        selectedId = getSignatureOfMail(pages[0].toplevel_object.id),
        menu = createSignatureMenu(selectedId, function (signature) {
          // value contains a new selection?
          if (selectedId !== signature.value) {
            if (deselectedId === -1) { // save the old selection once
              deselectedId = selectedId
            } else if (deselectedId === signature.value) {
              // the old one is selected again
              deselectedId = -1
            }
          }
          selectedId = signature.value
          $signature.empty().append(getSignatureBody(selectedId))
        })
      if (selectedId !== -1) {
          $signature.append(getSignatureBody(selectedId))
      }
      $parent.append($('<div>').append(menu.dom).append($signature))

      return function () { // create aggregation of last selection
        var values = []
        if (selectedId !== -1) {
          values.push(dm4c.REF_PREFIX + selectedId)
          if (deselectedId !== -1) {
            values.push(dm4c.DEL_PREFIX + deselectedId)
          }
        }
        return values
      }
    }
  })
}(jQuery, dm4c))
