/*
 * File name: MockTransport.java
 * 
 * Purpose: Use the MockTransport to mock sending data over the network.
 * 
 * © Copyright Intel Corporation. All rights reserved.
 * Intel Corporation, 2200 Mission College Boulevard,
 * Santa Clara, CA 95052-8119, USA
 */
package com.intel.jndn.mock;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;
import net.named_data.jndn.Data;
import net.named_data.jndn.ForwardingFlags;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.Node;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterest;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.encoding.WireFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Andrew Brown <andrew.brown@intel.com>
 */
public class MockFace {

  private static final Logger logger = LogManager.getLogger();
  private final Node node_;
  HashMap<String, Data> responseMap = new HashMap<>();
  HashMap<Long, MockOnInterestHandler> handlerMap = new HashMap<>();
  long lastRegisteredId = 0;

  /**
   * Create a new Face to mock communication over the network; all packets are
   * maintained in memory
   */
  public MockFace() {
    node_ = new Node(new MockTransport(), null);
  }

  /**
   * Add a response Data packet to send immediately when an Interest with a
   * matching name is received; will continue to respond with the same packet
   * over multiple requests. This will preempt any registered OnInterest
   * handlers.
   *
   * @param name
   * @param data
   */
  public void addResponse(Name name, Data data) {
    logger.debug("Added response for: " + name.toUri());
    responseMap.put(name.toUri(), data);
  }

  /**
   * Stop sending a response for the given name.
   *
   * @param name
   */
  public void removeResponse(Name name) {
    logger.debug("Removed response for: " + name.toUri());
    responseMap.remove(name);
  }

  /**
   * Handle incoming Interest packets; when an Interest is expressed through
   * expressInterest(), this will run to determine if: 1) any responses have
   * been registered or 2) if any OnInterest handlers have been registered.
   * If one of these two succeeds, this method then re-directs the Interest from 
   * traveling down the network stack and returns data.
   * 
   * @param interest
   */
  protected void handleIncomingRequests(Interest interest) {
    String interestName = interest.getName().toUri();
    long registeredPrefixId = findRegisteredHandler(interest);
    // check if response registered
    if (responseMap.containsKey(interestName)) {
      logger.debug("Found response for: " + interestName);
      Data data = responseMap.get(interestName);
      ((MockTransport) node_.getTransport()).respondWith(data);
    } 
    // check if handler registered
    else if (registeredPrefixId != -1) {
      logger.debug("Found handler for: " + interestName);
      MockOnInterestHandler handler = handlerMap.get(findRegisteredHandler(interest));
      handler.onInterest.onInterest(handler.prefix, interest, node_.getTransport(), registeredPrefixId);
    }
    // log failure
    else {
      logger.warn("No response found for interest (aborting): " + interestName);
    }
  }

  /**
   * Find a handler that matches the incoming interest; currently, the only
   * flags supported are the ChildInherit flags.
   * 
   * @param interest
   * @return 
   */
  protected long findRegisteredHandler(Interest interest) {
    for (Entry<Long, MockOnInterestHandler> entry : handlerMap.entrySet()) {
      MockOnInterestHandler handler = entry.getValue();
      if (handler.flags.getChildInherit() && handler.prefix.match(interest.getName())) {
        return entry.getKey();
      }
      if (handler.prefix.equals(interest.getName())) {
        return entry.getKey();
      }
    }
    return -1;
  }

  /**
   * Helper class for holding references to OnInterest handlers
   */
  class MockOnInterestHandler {

    Name prefix;
    OnInterest onInterest;
    ForwardingFlags flags;

    public MockOnInterestHandler(Name prefix, OnInterest onInterest, ForwardingFlags flags) {
      this.prefix = prefix;
      this.onInterest = onInterest;
      this.flags = flags;
    }
  }

  /**
   * Send the Interest through the transport, read the entire response and call
   * onData(interest, data).
   *
   * @param interest The Interest to send. This copies the Interest.
   * @param onData When a matching data packet is received, this calls
   * onData.onData(interest, data) where interest is the interest given to
   * expressInterest and data is the received Data object. NOTE: You must not
   * change the interest object - if you need to change it then make a copy.
   * @param onTimeout If the interest times out according to the interest
   * lifetime, this calls onTimeout.onTimeout(interest) where interest is the
   * interest given to expressInterest. If onTimeout is null, this does not use
   * it.
   * @param wireFormat A WireFormat object used to encode the message.
   * @return The pending interest ID which can be used with
   * removePendingInterest.
   * @throws IOException For I/O error in sending the interest.
   */
  public long expressInterest(Interest interest, OnData onData, OnTimeout onTimeout,
          WireFormat wireFormat) throws IOException {
    long id = node_.expressInterest(interest, onData, onTimeout, wireFormat);
    handleIncomingRequests(interest);
    return id;
  }

  /**
   * Send the Interest through the transport, read the entire response and call
   * onData(interest, data). This uses the default
   * WireFormat.getDefaultWireFormat().
   *
   * @param interest The Interest to send. This copies the Interest.
   * @param onData When a matching data packet is received, this calls
   * onData.onData(interest, data) where interest is the interest given to
   * expressInterest and data is the received Data object. NOTE: You must not
   * change the interest object - if you need to change it then make a copy.
   * @param onTimeout If the interest times out according to the interest
   * lifetime, this calls onTimeout.onTimeout(interest) where interest is the
   * interest given to expressInterest. If onTimeout is null, this does not use
   * it.
   * @return The pending interest ID which can be used with
   * removePendingInterest.
   * @throws IOException For I/O error in sending the interest.
   */
  public long expressInterest(Interest interest, OnData onData, OnTimeout onTimeout) throws IOException {
    return expressInterest(interest, onData, onTimeout, WireFormat.getDefaultWireFormat());
  }

  /**
   * Send the Interest through the transport, read the entire response and call
   * onData(interest, data). Ignore if the interest times out.
   *
   * @param interest The Interest to send. This copies the Interest.
   * @param onData When a matching data packet is received, this calls
   * onData.onData(interest, data) where interest is the interest given to
   * expressInterest and data is the received Data object. NOTE: You must not
   * change the interest object - if you need to change it then make a copy.
   * @param wireFormat A WireFormat object used to encode the message.
   * @return The pending interest ID which can be used with
   * removePendingInterest.
   * @throws IOException For I/O error in sending the interest.
   */
  public long expressInterest(Interest interest, OnData onData, WireFormat wireFormat) throws IOException {
    return expressInterest(interest, onData, null, wireFormat);
  }

  /**
   * Send the Interest through the transport, read the entire response and call
   * onData(interest, data). Ignore if the interest times out. This uses the
   * default WireFormat.getDefaultWireFormat().
   *
   * @param interest The Interest to send. This copies the Interest.
   * @param onData When a matching data packet is received, this calls
   * onData.onData(interest, data) where interest is the interest given to
   * expressInterest and data is the received Data object. NOTE: You must not
   * change the interest object - if you need to change it then make a copy.
   * @return The pending interest ID which can be used with
   * removePendingInterest.
   * @throws IOException For I/O error in sending the interest.
   */
  public long expressInterest(Interest interest, OnData onData) throws IOException {
    return expressInterest(interest, onData, null, WireFormat.getDefaultWireFormat());
  }

  /**
   * Encode name as an Interest. If interestTemplate is not null, use its
   * interest selectors. Send the interest through the transport, read the
   * entire response and call onData(interest, data).
   *
   * @param name A Name for the interest. This copies the Name.
   * @param interestTemplate If not null, copy interest selectors from the
   * template. This does not keep a pointer to the Interest object.
   * @param onData When a matching data packet is received, this calls
   * onData.onData(interest, data) where interest is the interest given to
   * expressInterest and data is the received Data object. NOTE: You must not
   * change the interest object - if you need to change it then make a copy.
   * @param onTimeout If the interest times out according to the interest
   * lifetime, this calls onTimeout.onTimeout(interest) where interest is the
   * interest given to expressInterest. If onTimeout is null, this does not use
   * it.
   * @param wireFormat A WireFormat object used to encode the message.
   * @return The pending interest ID which can be used with
   * removePendingInterest.
   * @throws IOException For I/O error in sending the interest.
   */
  public long expressInterest(Name name, Interest interestTemplate, OnData onData, OnTimeout onTimeout,
          WireFormat wireFormat) throws IOException {
    Interest interest = new Interest(name);
    if (interestTemplate != null) {
      interest.setMinSuffixComponents(interestTemplate.getMinSuffixComponents());
      interest.setMaxSuffixComponents(interestTemplate.getMaxSuffixComponents());
      interest.setKeyLocator(interestTemplate.getKeyLocator());
      interest.setExclude(interestTemplate.getExclude());
      interest.setChildSelector(interestTemplate.getChildSelector());
      interest.setMustBeFresh(interestTemplate.getMustBeFresh());
      interest.setScope(interestTemplate.getScope());
      interest.setInterestLifetimeMilliseconds(
              interestTemplate.getInterestLifetimeMilliseconds());
      // Don't copy the nonce.
    } else {
      interest.setInterestLifetimeMilliseconds(4000.0);
    }

    return expressInterest(interest, onData, onTimeout, wireFormat);
  }

  /**
   * Encode name as an Interest. If interestTemplate is not null, use its
   * interest selectors. Send the interest through the transport, read the
   * entire response and call onData(interest, data). Use a default interest
   * lifetime.
   *
   * @param name A Name for the interest. This copies the Name.
   * @param onData When a matching data packet is received, this calls
   * onData.onData(interest, data) where interest is the interest given to
   * expressInterest and data is the received Data object. NOTE: You must not
   * change the interest object - if you need to change it then make a copy.
   * @param onTimeout If the interest times out according to the interest
   * lifetime, this calls onTimeout.onTimeout(interest) where interest is the
   * interest given to expressInterest. If onTimeout is null, this does not use
   * it.
   * @param wireFormat A WireFormat object used to encode the message.
   * @return The pending interest ID which can be used with
   * removePendingInterest.
   * @throws IOException For I/O error in sending the interest.
   */
  public long expressInterest(Name name, OnData onData, OnTimeout onTimeout,
          WireFormat wireFormat) throws IOException {
    return expressInterest(name, null, onData, onTimeout, wireFormat);
  }

  /**
   * Encode name as an Interest. If interestTemplate is not null, use its
   * interest selectors. Send the interest through the transport, read the
   * entire response and call onData(interest, data). Ignore if the interest
   * times out.
   *
   * @param name A Name for the interest. This copies the Name.
   * @param interestTemplate If not null, copy interest selectors from the
   * template. This does not keep a pointer to the Interest object.
   * @param onData When a matching data packet is received, this calls
   * onData.onData(interest, data) where interest is the interest given to
   * expressInterest and data is the received Data object. NOTE: You must not
   * change the interest object - if you need to change it then make a copy.
   * @param wireFormat A WireFormat object used to encode the message.
   * @return The pending interest ID which can be used with
   * removePendingInterest.
   * @throws IOException For I/O error in sending the interest.
   */
  public long expressInterest(Name name, Interest interestTemplate, OnData onData,
          WireFormat wireFormat) throws IOException {
    return expressInterest(name, interestTemplate, onData, null, wireFormat);
  }

  /**
   * Encode name as an Interest. If interestTemplate is not null, use its
   * interest selectors. Send the interest through the transport, read the
   * entire response and call onData(interest, data). This uses the default
   * WireFormat.getDefaultWireFormat().
   *
   * @param name A Name for the interest. This copies the Name.
   * @param interestTemplate If not null, copy interest selectors from the
   * template. This does not keep a pointer to the Interest object.
   * @param onData When a matching data packet is received, this calls
   * onData.onData(interest, data) where interest is the interest given to
   * expressInterest and data is the received Data object. NOTE: You must not
   * change the interest object - if you need to change it then make a copy.
   * @param onTimeout If the interest times out according to the interest
   * lifetime, this calls onTimeout.onTimeout(interest) where interest is the
   * interest given to expressInterest. If onTimeout is null, this does not use
   * it.
   * @return The pending interest ID which can be used with
   * removePendingInterest.
   * @throws IOException For I/O error in sending the interest.
   */
  public long expressInterest(Name name, Interest interestTemplate, OnData onData,
          OnTimeout onTimeout) throws IOException {
    return expressInterest(name, interestTemplate, onData, onTimeout,
            WireFormat.getDefaultWireFormat());
  }

  /**
   * Encode name as an Interest. If interestTemplate is not null, use its
   * interest selectors. Send the interest through the transport, read the
   * entire response and call onData(interest, data). Ignore if the interest
   * times out. This uses the default WireFormat.getDefaultWireFormat().
   *
   * @param name A Name for the interest. This copies the Name.
   * @param interestTemplate If not null, copy interest selectors from the
   * template. This does not keep a pointer to the Interest object.
   * @param onData When a matching data packet is received, this calls
   * onData.onData(interest, data) where interest is the interest given to
   * expressInterest and data is the received Data object. NOTE: You must not
   * change the interest object - if you need to change it then make a copy.
   * @return The pending interest ID which can be used with
   * removePendingInterest.
   * @throws IOException For I/O error in sending the interest.
   */
  public long expressInterest(Name name, Interest interestTemplate, OnData onData) throws IOException {
    return expressInterest(name, interestTemplate, onData, null, WireFormat.getDefaultWireFormat());
  }

  /**
   * Encode name as an Interest. If interestTemplate is not null, use its
   * interest selectors. Send the interest through the transport, read the
   * entire response and call onData(interest, data). Use a default interest
   * lifetime. This uses the default WireFormat.getDefaultWireFormat().
   *
   * @param name A Name for the interest. This copies the Name.
   * @param onData When a matching data packet is received, this calls
   * onData.onData(interest, data) where interest is the interest given to
   * expressInterest and data is the received Data object. NOTE: You must not
   * change the interest object - if you need to change it then make a copy.
   * @param onTimeout If the interest times out according to the interest
   * lifetime, this calls onTimeout.onTimeout(interest) where interest is the
   * interest given to expressInterest. If onTimeout is null, this does not use
   * it.
   * @return The pending interest ID which can be used with
   * removePendingInterest.
   * @throws IOException For I/O error in sending the interest.
   */
  public long expressInterest(Name name, OnData onData, OnTimeout onTimeout) throws IOException {
    return expressInterest(name, null, onData, onTimeout, WireFormat.getDefaultWireFormat());
  }

  /**
   * Encode name as an Interest. If interestTemplate is not null, use its
   * interest selectors. Send the interest through the transport, read the
   * entire response and call onData(interest, data). Use a default interest
   * lifetime. Ignore if the interest times out.
   *
   * @param name A Name for the interest. This copies the Name.
   * @param onData When a matching data packet is received, this calls
   * onData.onData(interest, data) where interest is the interest given to
   * expressInterest and data is the received Data object. NOTE: You must not
   * change the interest object - if you need to change it then make a copy.
   * @param wireFormat A WireFormat object used to encode the message.
   * @return The pending interest ID which can be used with
   * removePendingInterest.
   * @throws IOException For I/O error in sending the interest.
   */
  public long expressInterest(Name name, OnData onData, WireFormat wireFormat) throws IOException {
    return expressInterest(name, null, onData, null, wireFormat);
  }

  /**
   * Encode name as an Interest. If interestTemplate is not null, use its
   * interest selectors. Send the interest through the transport, read the
   * entire response and call onData(interest, data). Use a default interest
   * lifetime. Ignore if the interest times out. This uses the default
   * WireFormat.getDefaultWireFormat().
   *
   * @param name A Name for the interest. This copies the Name.
   * @param onData When a matching data packet is received, this calls
   * onData.onData(interest, data) where interest is the interest given to
   * expressInterest and data is the received Data object. NOTE: You must not
   * change the interest object - if you need to change it then make a copy.
   * @return The pending interest ID which can be used with
   * removePendingInterest.
   * @throws IOException For I/O error in sending the interest.
   */
  public long expressInterest(Name name, OnData onData) throws IOException {
    return expressInterest(name, null, onData, null, WireFormat.getDefaultWireFormat());
  }

  /**
   * Remove the pending interest entry with the pendingInterestId from the
   * pending interest table. This does not affect another pending interest with
   * a different pendingInterestId, even if it has the same interest name. If
   * there is no entry with the pendingInterestId, do nothing.
   *
   * @param pendingInterestId The ID returned from expressInterest.
   */
  public void removePendingInterest(long pendingInterestId) {
    node_.removePendingInterest(pendingInterestId);
  }

  /**
   * Register prefix with the connected NDN hub and call onInterest when a
   * matching interest is received. If you have not called
   * setCommandSigningInfo, this assumes you are connecting to NDNx. If you have
   * called setCommandSigningInfo, this first sends an NFD registration request,
   * and if that times out then this sends an NDNx registration request. If you
   * need to register a prefix with NFD, you must first call
   * setCommandSigningInfo.
   *
   * @param prefix A Name for the prefix to register. This copies the Name.
   * @param onInterest When an interest is received which matches the name
   * prefix, this calls onInterest.onInterest(prefix, interest, transport,
   * registeredPrefixId). NOTE: You must not change the prefix object - if you
   * need to change it then make a copy.
   * @param onRegisterFailed If register prefix fails for any reason, this calls
   * onRegisterFailed.onRegisterFailed(prefix).
   * @param flags The flags for finer control of which interests are forwarded
   * to the application.
   * @param wireFormat A WireFormat object used to encode the message.
   * @return The lastRegisteredId prefix ID which can be used with
   * removeRegisteredPrefix.
   * @throws IOException For I/O error in sending the registration request.
   * @throws SecurityException If signing a command interest for NFD and cannot
   * find the private key for the certificateName.
   */
  public long registerPrefix(Name prefix, OnInterest onInterest, OnRegisterFailed onRegisterFailed,
          ForwardingFlags flags, WireFormat wireFormat) throws IOException, net.named_data.jndn.security.SecurityException {
    lastRegisteredId++;
    handlerMap.put(lastRegisteredId, new MockOnInterestHandler(prefix, onInterest, flags));
    return lastRegisteredId;
  }

  /**
   * Register prefix with the connected NDN hub and call onInterest when a
   * matching interest is received. This uses the default
   * WireFormat.getDefaultWireFormat().
   *
   * @param prefix A Name for the prefix to register. This copies the Name.
   * @param onInterest When an interest is received which matches the name
   * prefix, this calls onInterest.onInterest(prefix, interest, transport,
   * registeredPrefixId). NOTE: You must not change the prefix object - if you
   * need to change it then make a copy.
   * @param onRegisterFailed If register prefix fails for any reason, this calls
   * onRegisterFailed.onRegisterFailed(prefix).
   * @param flags The flags for finer control of which interests are forwarded
   * to the application.
   * @return The lastRegisteredId prefix ID which can be used with
   * removeRegisteredPrefix.
   * @throws IOException For I/O error in sending the registration request.
   */
  public long registerPrefix(Name prefix, OnInterest onInterest, OnRegisterFailed onRegisterFailed,
          ForwardingFlags flags) throws IOException, net.named_data.jndn.security.SecurityException {
    return registerPrefix(prefix, onInterest, onRegisterFailed, flags,
            WireFormat.getDefaultWireFormat());
  }

  /**
   * Register prefix with the connected NDN hub and call onInterest when a
   * matching interest is received. Use default ForwardingFlags.
   *
   * @param prefix A Name for the prefix to register. This copies the Name.
   * @param onInterest When an interest is received which matches the name
   * prefix, this calls onInterest.onInterest(prefix, interest, transport,
   * registeredPrefixId). NOTE: You must not change the prefix object - if you
   * need to change it then make a copy.
   * @param onRegisterFailed If register prefix fails for any reason, this calls
   * onRegisterFailed.onRegisterFailed(prefix).
   * @param wireFormat A WireFormat object used to encode the message.
   * @return The lastRegisteredId prefix ID which can be used with
   * removeRegisteredPrefix.
   * @throws IOException For I/O error in sending the registration request.
   * @throws SecurityException If signing a command interest for NFD and cannot
   * find the private key for the certificateName.
   */
  public long registerPrefix(Name prefix, OnInterest onInterest, OnRegisterFailed onRegisterFailed,
          WireFormat wireFormat) throws IOException, net.named_data.jndn.security.SecurityException {
    return registerPrefix(prefix, onInterest, onRegisterFailed, new ForwardingFlags(), wireFormat);
  }

  /**
   * Register prefix with the connected NDN hub and call onInterest when a
   * matching interest is received. This uses the default
   * WireFormat.getDefaultWireFormat(). Use default ForwardingFlags.
   *
   * @param prefix A Name for the prefix to register. This copies the Name.
   * @param onInterest When an interest is received which matches the name
   * prefix, this calls onInterest.onInterest(prefix, interest, transport,
   * registeredPrefixId). NOTE: You must not change the prefix object - if you
   * need to change it then make a copy.
   * @param onRegisterFailed If register prefix fails for any reason, this calls
   * onRegisterFailed.onRegisterFailed(prefix).
   * @return The lastRegisteredId prefix ID which can be used with
   * removeRegisteredPrefix.
   * @throws IOException For I/O error in sending the registration request.
   * @throws SecurityException If signing a command interest for NFD and cannot
   * find the private key for the certificateName.
   */
  public long registerPrefix(Name prefix, OnInterest onInterest,
          OnRegisterFailed onRegisterFailed) throws IOException, net.named_data.jndn.security.SecurityException {
    return registerPrefix(prefix, onInterest, onRegisterFailed, new ForwardingFlags(),
            WireFormat.getDefaultWireFormat());
  }

  /**
   * Remove the lastRegisteredId prefix entry with the registeredPrefixId from
   * the lastRegisteredId prefix table. This does not affect another
   * lastRegisteredId prefix with a different registeredPrefixId, even if it has
   * the same prefix name. If there is no entry with the registeredPrefixId, do
   * nothing.
   *
   * @param registeredPrefixId The ID returned from registerPrefix.
   */
  public void removeRegisteredPrefix(long registeredPrefixId) {
    handlerMap.remove(registeredPrefixId);
  }

  /**
   * Process any packets to receive and call callbacks such as onData,
   * onInterest or onTimeout. This returns immediately if there is no data to
   * receive. This blocks while calling the callbacks. You should repeatedly
   * call this from an event loop, with calls to sleep as needed so that the
   * loop doesn’t use 100% of the CPU. Since processEvents modifies the pending
   * interest table, your application should make sure that it calls
   * processEvents in the same thread as expressInterest (which also modifies
   * the pending interest table). This may throw an exception for reading data
   * or in the callback for processing the data. If you call this from an main
   * event loop, you may want to catch and log/disregard all exceptions.
   */
  public void processEvents() throws IOException, EncodingException {
    // Just call Node's processEvents.
    node_.processEvents();
  }

  /**
   * Shut down and disconnect this Face.
   */
  public void shutdown() {
    node_.shutdown();
  }
}