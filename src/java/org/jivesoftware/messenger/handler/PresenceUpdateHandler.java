/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.handler;

import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.roster.Roster;
import org.jivesoftware.messenger.roster.RosterItem;
import org.jivesoftware.messenger.roster.RosterManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.util.ConcurrentHashSet;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the presence protocol. Clients use this protocol to
 * update presence and roster information.
 * <p/>
 * The handler must properly detect the presence type, update the user's roster,
 * and inform presence subscribers of the session's updated presence
 * status. Presence serves many purposes in Jabber so this handler will
 * likely be the most complex of all handlers in the server.
 * <p/>
 * There are four basic types of presence updates:
 * <ul>
 * <li>Simple presence updates - addressed to the server (or to address), these updates
 * are properly addressed by the server, and multicast to
 * interested subscribers on the user's roster. An empty, missing,
 * or "unavailable" type attribute indicates a simple update (there
 * is no "available" type although it should be accepted by the server.
 * <li>Directed presence updates - addressed to particular jabber entities,
 * these presence updates are properly addressed and directly delivered
 * to the entity without broadcast to roster subscribers. Any update type
 * is possible except those reserved for subscription requests.
 * <li>Subscription requests - these updates request presence subscription
 * status changes. Such requests always affect the roster.  The server must:
 * <ul>
 * <li>update the roster with the proper subscriber info
 * <li>push the roster changes to the user
 * <li>forward the update to the correct parties.
 * </ul>
 * The valid types include "subscribe", "subscribed", "unsubscribed",
 * and "unsubscribe".
 * <li>XMPPServer probes - Provides a mechanism for servers to query the presence
 * status of users on another server. This allows users to immediately
 * know the presence status of users when they come online rather than way
 * for a presence update broadcast from the other server or tracking them
 * as they are received.  Requires S2S capabilities.
 * </ul>
 *
 * @author Iain Shigeoka
 *
 * todo Support probe packets (only needed with s2s)
 */
public class PresenceUpdateHandler extends BasicModule implements ChannelHandler {

    private Map<String, WeakHashMap<ChannelHandler, Set<String>>> directedPresences;

    private RosterManager rosterManager;
    private XMPPServer localServer;
    private PresenceManager presenceManager;
    private PacketDeliverer deliverer;
    private OfflineMessageStore messageStore;
    private SessionManager sessionManager;

    public PresenceUpdateHandler() {
        super("Presence update handler");
        directedPresences = new ConcurrentHashMap<String, WeakHashMap<ChannelHandler, Set<String>>>();
    }

    public void process(Packet xmppPacket) throws UnauthorizedException, PacketException {
        Presence presence = (Presence)xmppPacket;
        try {
            ClientSession session = sessionManager.getSession(presence.getFrom());
            Presence.Type type = presence.getType();
            // Available
            if (type == null) {
                broadcastUpdate(presence.createCopy());
                if (session != null) {
                    session.setPresence(presence);
                    if (!session.isInitialized()) {
                        initSession(session);
                        session.setInitialized(true);
                    }
                }
                // Notify the presence manager that the user is now available. The manager may
                // remove the last presence status sent by the user when he went offline.
                presenceManager.userAvailable(presence);
            }
            else if (Presence.Type.unavailable == type) {
                broadcastUpdate(presence.createCopy());
                broadcastUnavailableForDirectedPresences(presence);
                if (session != null) {
                    session.setPresence(presence);
                }
                // Notify the presence manager that the user is now unavailable. The manager may
                // save the last presence status sent by the user and keep track when the user
                // went offline.
                presenceManager.userUnavailable(presence);
            }
            else {
                presence = presence.createCopy();
                if (session != null) {
                    presence.setFrom(new JID(null, session.getServerName(), null));
                    presence.setTo(session.getAddress());
                }
                else {
                    JID sender = presence.getFrom();
                    presence.setFrom(presence.getTo());
                    presence.setTo(sender);
                }
                presence.setError(PacketError.Condition.bad_request);
                deliverer.deliver(presence);
            }

        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    /**
     * Handle presence updates that affect roster subscriptions.
     *
     * @param presence The presence presence to handle
     */
    public void process(Presence presence) throws PacketException {
        try {
            process((Packet)presence);
        }
        catch (UnauthorizedException e) {
            try {
                Session session = sessionManager.getSession(presence.getFrom());
                presence = presence.createCopy();
                if (session != null) {
                    presence.setFrom(new JID(null, session.getServerName(), null));
                    presence.setTo(session.getAddress());
                }
                else {
                    JID sender = presence.getFrom();
                    presence.setFrom(presence.getTo());
                    presence.setTo(sender);
                }
                presence.setError(PacketError.Condition.not_authorized);
                deliverer.deliver(presence);
            }
            catch (Exception err) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), err);
            }
        }
    }

    /**
     * A session that has transitioned to available status must be initialized.
     * This includes:
     * <ul>
     * <li>Sending all offline presence subscription requests</li>
     * <li>Sending offline messages</li>
     * </ul>
     *
     * @param session The session being updated
     * @throws UnauthorizedException If the caller doesn't have the right permissions
     * @throws UserNotFoundException If the user being updated does not exist
     */
    private void initSession(Session session)  throws UnauthorizedException, UserNotFoundException {

        // Only user sessions need to be authenticated
        if (session.getAddress().getNode() != null && !"".equals(session.getAddress().getNode())) {
            String username = session.getAddress().getNode();
            Roster roster = rosterManager.getRoster(username);
            for (RosterItem item : roster.getRosterItems()) {
                if (item.getRecvStatus() == RosterItem.RECV_SUBSCRIBE) {
                    session.process(createSubscribePresence(item.getJid(), true));
                }
                else if (item.getRecvStatus() == RosterItem.RECV_UNSUBSCRIBE) {
                    session.process(createSubscribePresence(item.getJid(), false));
                }
                if (item.getSubStatus() == RosterItem.SUB_TO
                        || item.getSubStatus() == RosterItem.SUB_BOTH) {
                    presenceManager.probePresence(session.getAddress(), item.getJid());
                }
            }
            // deliver offline messages if any
            Collection<Message> messages = messageStore.getMessages(username);
            for (Message message : messages) {
                session.process(message);
            }
        }
    }

    public Presence createSubscribePresence(JID senderAddress, boolean isSubscribe) {
        Presence presence = new Presence();
        presence.setFrom(senderAddress);
        if (isSubscribe) {
            presence.setType(Presence.Type.subscribe);
        }
        else {
            presence.setType(Presence.Type.unsubscribe);
        }
        return presence;
    }

    /**
     * Broadcast the given update to all subscribers. We need to:
     * <ul>
     * <li>Query the roster table for subscribers</li>
     * <li>Iterate through the list and send the update to each subscriber</li>
     * </ul>
     * <p/>
     * Is there a safe way to cache the query results while maintaining
     * integrity with roster changes?
     *
     * @param update The update to broadcast
     */
    private void broadcastUpdate(Presence update) throws PacketException {
        if (update.getFrom() == null) {
            return;
        }
        if (localServer.isLocal(update.getFrom())) {
            // Local updates can simply run through the roster of the local user
            String name = update.getFrom().getNode();
            try {
                if (name != null && !"".equals(name)) {
                    name = name.toLowerCase();
                    Roster roster = rosterManager.getRoster(name);
                    roster.broadcastPresence(update);
                }
            }
            catch (UserNotFoundException e) {
                Log.warn("Presence being sent from unknown user " + name, e);
            }
            catch (PacketException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
        else {
            // Foreign updates will do a reverse lookup of entries in rosters
            // on the server
            Log.warn("Presence requested from server "
                    + localServer.getServerInfo().getName()
                    + " by unknown user: " + update.getFrom());
            /*
            Connection con = null;
            PreparedStatement pstmt = null;
            try {

                pstmt = con.prepareStatement(GET_ROSTER_SUBS);
                pstmt.setString(1, update.getSender().toBareString().toLowerCase());
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()){
                    long userID = rs.getLong(1);
                    try {
                        User user = server.getUserManager().getUser(userID);

                        update.setRecipient(user.getAddress());
                        server.getSessionManager().userBroadcast(user.getUsername(),
                                                                update.getPacket());
                    } catch (UserNotFoundException e) {
                        Log.error(LocaleUtils.getLocalizedString("admin.error"),e);
                    } catch (UnauthorizedException e) {
                        Log.error(LocaleUtils.getLocalizedString("admin.error"),e);
                    }
                }
            }
            catch (SQLException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"),e);
            }
            */
        }
    }

    /**
     * Notification method sent to this handler when a user has sent a directed
     * presence to an entity. If the sender of the presence is local (to this server)
     * and the target entity does not belong to the user's roster then update the
     * registry of sent directed presences by the user.
     *
     * @param update  the directed Presence sent by the user to an entity.
     * @param handler the handler that routed the presence to the entity.
     * @param jid     the jid that the handler has processed
     */
    public void directedPresenceSent(Presence update, ChannelHandler handler, String jid) {
        if (update.getFrom() == null) {
            return;
        }
        if (localServer.isLocal(update.getFrom())) {
            boolean keepTrack = false;
            WeakHashMap<ChannelHandler, Set<String>> map;
            String name = update.getFrom().getNode();
            if (name != null && !"".equals(name)) {
                name = name.toLowerCase();
                try {
                    Roster roster = rosterManager.getRoster(name);
                    // If the directed presence was sent to an entity that is not in the user's
                    // roster, keep a registry of this so that when the user goes offline we will
                    // be able to send the unavailable presence to the entity
                    if (!roster.isRosterItem(update.getTo())) {
                        keepTrack = true;
                    }
                }
                catch (UserNotFoundException e) {
                    Log.warn("Presence being sent from unknown user " + name, e);
                }
                catch (PacketException e) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                }

            }
            else if (update.getFrom().getResource() != null){
                // Keep always track of anonymous users directed presences
                keepTrack = true;
            }
            if (keepTrack) {
                map = directedPresences.get(update.getFrom().toString());
                if (map == null) {
                    // We are using a set to avoid duplicate jids in case the user
                    // sends several directed presences to the same handler. The Map also
                    // ensures that if the user sends several presences to the same handler
                    // we will have only one entry in the Map
                    map = new WeakHashMap<ChannelHandler, Set<String>>();
                    map.put(handler, new ConcurrentHashSet<String>());
                    directedPresences.put(update.getFrom().toString(), map);
                }
                if (Presence.Type.unavailable.equals(update.getType())) {
                    // It's a directed unavailable presence
                    if (handler instanceof ClientSession) {
                        // Client sessions will receive only presences to the same JID (the
                        // address of the session) so remove the handler from the map
                        map.remove(handler);
                        if (map.isEmpty()) {
                            // Remove the user from the registry since the list of directed
                            // presences is empty
                            directedPresences.remove(update.getFrom().toString());
                        }
                    }
                    else {
                        // A service may receive presences for many JIDs so in this case we
                        // just need to remove the jid that has received a directed
                        // unavailable presence
                        Set<String> jids = map.get(handler);
                        if (jids != null) {
                            jids.remove(jid);
                        }

                    }
                }
                else {
                    // Add the handler to the list of handler that processed the directed
                    // presence sent by the user. This handler will be used to send
                    // the unavailable presence when the user goes offline
                    if (map.get(handler) == null) {
                        map.put(handler, new ConcurrentHashSet<String>());
                    }
                    map.get(handler).add(jid);
                }
            }
        }
    }

    /**
     * Sends an unavailable presence to the entities that received a directed (available) presence
     * by the user that is now going offline.
     *
     * @param update the unavailable presence sent by the user.
     */
    private void broadcastUnavailableForDirectedPresences(Presence update) {
        if (update.getFrom() == null) {
            return;
        }
        if (localServer.isLocal(update.getFrom())) {
            Map<ChannelHandler, Set<String>> map = directedPresences.get(update.getFrom().toString());
            if (map != null) {
                // Iterate over all the entities that the user sent a directed presence
                for (ChannelHandler handler : new HashSet<ChannelHandler>(map.keySet())) {
                    Set<String> jids = map.get(handler);
                    if (jids == null) {
                        continue;
                    }
                    for (String jid : jids) {
                        Presence presence = update.createCopy();
                        presence.setTo(new JID(jid));
                        try {
                            handler.process(presence);
                        }
                        catch (UnauthorizedException ue) {
                            Log.error(ue);
                        }
                    }
                }
                // Remove the registry of directed presences of this user
                directedPresences.remove(update.getFrom().toString());
            }
        }
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        localServer = server;
        rosterManager = server.getRosterManager();
        presenceManager = server.getPresenceManager();
        deliverer = server.getPacketDeliverer();
        messageStore = server.getOfflineMessageStore();
        sessionManager = server.getSessionManager();
    }
}
