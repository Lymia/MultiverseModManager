-- Copyright (c) 2015-2016 Lymia Alusyia <lymia@lymiahugs.com>
--
-- Permission is hereby granted, free of charge, to any person obtaining a copy
-- of this software and associated documentation files (the "Software"), to deal
-- in the Software without restriction, including without limitation the rights
-- to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
-- copies of the Software, and to permit persons to whom the Software is
-- furnished to do so, subject to the following conditions:
--
-- The above copyright notice and this permission notice shall be included in
-- all copies or substantial portions of the Software.
--
-- THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
-- IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
-- FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
-- AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
-- LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
-- OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
-- THE SOFTWARE.

local marker = "mppatch_command:yLsMoqQAirGJ4RBQv8URAwcu6RXdqN6v:"

local chatProtocolCommands = {}
function _mpPatch.registerChatCommand(id)
    local handlers = {}
    chatProtocolCommands[id] = function(...)
        for _, handler in ipairs(handlers) do
            handler(...)
        end
    end
    return setmetatable({
        send = function(data)
            _mpPatch.debugPrint("Sending MPPatch chat command: "..id..", data = "..(data or "<no data>"))
            _mpPatch.sendChatCommand(id, data)
        end,
        registerHandler = function(fn)
            _mpPatch.debugPrint("Registering handler for chat command "..id)
            table.insert(handlers, fn)
        end
    }, {
      __call = function(t, ...) return t.send(...) end
    })
end
function _mpPatch.sendChatCommand(id, data)
    Network.SendChat(marker..id..":"..(data or ""))
end

function _mpPatch.interceptChatFunction(fn, condition, noCheckHide)
    condition = condition or function() return true end
    local function chatFn(...)
        local fromPlayer, _, text = ...
        if (noCheckHide or not ContextPtr:IsHidden()) and condition(fromPlayer) then
            local textHead, textTail = text:sub(1, marker:len()), text:sub(marker:len() + 1)
            if textHead == marker then
                local split = textTail:find(":")
                local command, data = textTail:sub(1, split - 1), textTail:sub(split + 1)
                _mpPatch.debugPrint("Got MPPatch chat command: "..command..", data = "..data)
                local fn = chatProtocolCommands[command]
                if not fn then
                    return
                else
                    return fn(data, ...)
                end
            end
        end
        return fn(...)
    end
    Events.GameMessageChat.Remove(fn)
    Events.GameMessageChat.Add(chatFn)
    return chatFn
end

-- commands
_mpPatch.net = {}
_mpPatch.net.sendPlayerData       = _mpPatch.registerChatCommand("sendPlayerData"      )
_mpPatch.net.startLaunchCountdown = _mpPatch.registerChatCommand("startLaunchCountdown")