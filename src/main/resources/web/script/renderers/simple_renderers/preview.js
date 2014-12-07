/*global jQuery, dm4c*/
(function ($, dm4c) {

  
  
  // --- Signature Renderer Helper Methods 

  function getSignature(signatureId) {
    return dm4c.restc.get_topic_by_id(signatureId, true)
  }

  function getSignatureBody(signature) {
    if (signature && signature.childs['dm4.mail.body']) {
      return signature.childs['dm4.mail.body'].value || 'empty signature'
    } else {
      return 'invalid signature topic selected' // invalid signature
    }
  }

  function addSignatureOfMail(mail, $parent) {
    var $signature = $('<div>').addClass('signature')
    var signatures = mail.childs['dm4.mail.signature']
    if (signatures && signatures.length === 1) {
        // FIXME recursive childs fetch after save action needed
        var signature = getSignature(signatures[0].id)
        $signature.html(getSignatureBody(signature))
    }
    $parent.append($signature)
  }

  dm4c.add_listener('render_mail_signature', function (signatureId) {
    var signature = getSignature(signatureId)
    // FIXME scope the selection of the signature element
    $('.signature').empty().html(getSignatureBody(signature))
  })
  
  
  
  // --- Signature Multi Renderer Implementation

  dm4c.add_simple_renderer('dm4.mail.signature.preview', {

    render_info: function (model, $parent) {
      // ### is dummy field (can just be rendered part of its parent object)
      addSignatureOfMail(model.parent.object, $parent)
    },

    render_form: function (model, $parent) {
      addSignatureOfMail(model.parent.object, $parent)
      return function () {
        return true // set dummy field after edit
      }
    }

  })

}(jQuery, dm4c))
