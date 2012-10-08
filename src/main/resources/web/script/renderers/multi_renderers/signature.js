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
      return 'invalid signature'
    }
  }

  function getSignatureOfMail(mailId) {
    return dm4c.restc.get_topic_related_topics(mailId, {
      assoc_type_uri: 'dm4.core.aggregation',
      my_role_type_uri: 'dm4.core.whole',
      others_role_type_uri: 'dm4.core.part',
      others_topic_type_uri: 'dm4.mail.signature'
    }, false, 1).items[0] // the first one
  }

  function createSignatureMenu(selected, onChoose) {
    var menu = dm4c.ui.menu(onChoose)
    $.each(getAllSignatures(), function (t, topic) {
      menu.add_item({ label: topic.value, value: topic.id })
    })
    menu.select(selected)
    return menu
  }

  dm4c.add_multi_renderer('dm4.mail.signature.renderer', {

    render_info: function (pages, $parent, level) {
      $.each(pages, function (p, page) { // only one signature is associated
        dm4c.render.field_label(page.topic_type.value, $parent)
        if (page.topic.id !== -1) { // ignore empty default page model
          $parent.append($('<div>').append(getSignatureBody(page.topic.id)))
        }
      })
    },

    render_form: function (pages, $parent, level) {
      var deselectedId = 0,
        selectedId = getSignatureOfMail(pages[0].toplevel_topic.id).id,
        $signature = $('<div>').append(getSignatureBody(selectedId)),
        menu = createSignatureMenu(selectedId, function (signature) {
          if (selectedId !== -1 && selectedId !== signature.value) {
            deselectedId = selectedId
          }
          selectedId = signature.value
          $signature.empty().append(getSignatureBody(selectedId))
        })

      $parent.append($('<div>').append(menu.dom).append($signature))

      return function () { // create aggregation of last selection
        var values = [dm4c.REF_PREFIX + selectedId]
        if (deselectedId !== 0) {
          values.push(dm4c.DEL_PREFIX + deselectedId)
        }
        return values
      }
    }
  })
}(jQuery, dm4c))
