/*
 * Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.axis2.transport.rabbitmq;

import com.rabbitmq.client.AMQP;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.transport.base.BaseConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This is the RabbitMQ AMQP message receiver which is invoked when a message is received. This processes
 * the message through the axis2 engine
 */
public class RabbitMQMessageReceiver {
    private static final Log log = LogFactory.getLog(RabbitMQMessageReceiver.class);
    private final RabbitMQEndpoint endpoint;
    private final RabbitMQListener listener;

    /**
     * Create a new RabbitMQMessage receiver
     *
     * @param listener the AMQP transport Listener
     * @param endpoint the RabbitMQEndpoint definition to be used
     */
    public RabbitMQMessageReceiver(RabbitMQListener listener, RabbitMQEndpoint endpoint) {
        this.endpoint = endpoint;
        this.listener = listener;
    }

    /**
     * Process a new message received
     *
     * @param properties the AMQP basic properties
     * @param body       the message body
     */
    public AcknowledgementMode onMessage(AMQP.BasicProperties properties, byte[] body) {
        AcknowledgementMode acknowledgementMode = AcknowledgementMode.REQUEUE_FALSE;
        try {
            acknowledgementMode = processThroughAxisEngine(properties, body);
        } catch (AxisFault axisFault) {
            log.error("Error while processing message", axisFault);
        }
        return acknowledgementMode;
    }

    /**
     * Process the new message through Axis2
     *
     * @param properties the AMQP basic properties
     * @param body       the message body
     * @return true if no mediation errors
     * @throws AxisFault on Axis2 errors
     */
    private AcknowledgementMode processThroughAxisEngine(AMQP.BasicProperties properties, byte[] body) throws AxisFault {
        MessageContext msgContext = endpoint.createMessageContext();
        // Get transport properties for configured contentType
        String contentTypeParameter = null;
        try {
            contentTypeParameter = endpoint.getServiceTaskManager().getRabbitMQProperties().get(RabbitMQConstants.CONTENT_TYPE);
            msgContext.setProperty(RabbitMQConstants.CONTENT_TYPE, contentTypeParameter);
        } catch(Exception e)    {
            log.warn("Error while getting content-type from proxy", e);
        }
        String contentType = RabbitMQUtils.buildMessageWithReplyTo(properties, body, msgContext);
        try {
            listener.handleIncomingMessage(
                    msgContext,
                    RabbitMQUtils.getTransportHeaders(properties),
                    RabbitMQUtils.getSoapAction(properties),
                    contentType);
            Object rollbackProperty = msgContext.getProperty(BaseConstants.SET_ROLLBACK_ONLY);
            if ((rollbackProperty instanceof Boolean && ((Boolean) rollbackProperty)) ||
                    (rollbackProperty instanceof String && Boolean.parseBoolean((String) rollbackProperty))) {
                return AcknowledgementMode.REQUEUE_FALSE;
            }
            Object requeueOnRollbackProperty = msgContext.getProperty(RabbitMQConstants.SET_REQUEUE_ON_ROLLBACK);
            if ((requeueOnRollbackProperty instanceof Boolean && ((Boolean) requeueOnRollbackProperty)) ||
                    (requeueOnRollbackProperty instanceof String &&
                            Boolean.parseBoolean((String) requeueOnRollbackProperty))) {
                return AcknowledgementMode.REQUEUE_TRUE;
            }
        } catch (AxisFault axisFault) {
            log.error("Error when trying to read incoming message ...", axisFault);
            return AcknowledgementMode.REQUEUE_FALSE;
        }
        return AcknowledgementMode.ACKNOWLEDGE;
    }
}
