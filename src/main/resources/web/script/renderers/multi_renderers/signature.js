/*global jQuery, dm4c*/
(function ($, dm4c) {

    function getAllSignatures() {
        return dm4c.restc.get_topics('dm4.mail.signature', false, true)
    }

    function getSignatureIdOfMail(mail) {
        var signatures = mail.childs['dm4.mail.signature']
        if (signatures && signatures.length === 1) {
            return signatures[0].id // the first one
        } else {
            return -1
        }
    }

    function createSignatureMenu(selected, onChoose) {
        var menu = dm4c.ui.menu(onChoose)
        $.each(getAllSignatures(), function (t, topic) {
            menu.add_item({label: topic.value, value: topic.id})
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
                $parent.append($('<div>').html(pages[0].object.value))
            }
        },
        render_form: function (pages, $parent, level) {
            var deselectedId = -1
            var selectedId = getSignatureIdOfMail(pages[0].parent.object)
            dm4c.render.field_label("Signature", $parent)
            var menu = createSignatureMenu(selectedId, function (signature) {
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
                dm4c.fire_event('render_mail_signature', selectedId)
            })
            $parent.append(menu.dom)

            return function () { // creates aggregation of last selection
                var values = []
                if (selectedId !== -1) {
                    values.push(dm4c.REF_ID_PREFIX + "" + selectedId)
                    if (deselectedId !== -1) {
                        values.push(dm4c.DEL_ID_PREFIX + "" + deselectedId)
                    }
                }
                return values
            }
        }
    })
}(jQuery, dm4c))
