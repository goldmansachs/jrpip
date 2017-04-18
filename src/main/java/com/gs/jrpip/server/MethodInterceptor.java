/*
  Copyright 2017 Goldman Sachs.
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
 */

package com.gs.jrpip.server;

import java.lang.reflect.Method;

/**
 * When configured on the JRPIP servlet, the interceptor will be executed for every JRPIP request,
 * providing the ability log, trigger a side effect, or even stop the execution of the method.
 *
 * In the case the method fails, The interceptor allow to replace the exception with another exception.
 */
public interface MethodInterceptor
{
    /**
     * Evaluated before the JRPIP method is evaluated.  Any exception thrown on this method will prevent the
     * JRPIP method evaluation, and the exception been thrown will bubble up to the caller
     *
     * @param context context of the JRPIP method requests been intercepted
     * @param method JRPIP method been executed
     * @param arguments arguments for the JRPIP method evaluation
     */
    void beforeMethodEvaluation(JrpipRequestContext context, Method method, Object[] arguments);

    /**
     * Evaluated after the JRPIP method is evaluated, and finished successfully. Any exception thrown on this method
     * will replace the successful result with the exception, bubbling up to the client.
     *
     * If this method throws, {@link #afterMethodEvaluationFails(JrpipRequestContext, java.lang.reflect.Method, Object[], Throwable)} wont be executed.
     * This means only one afterMethod will be executed for every request, and not both!
     *
     * @param context context of the JRPIP method requests been intercepted
     * @param method JRPIP method been executed
     * @param arguments arguments for the JRPIP method evaluation
     * @param returnValue value retained by the evaluation of the JRPIP method
     */
    void afterMethodEvaluationFinishes(JrpipRequestContext context, Method method, Object[] arguments, Object returnValue);

    /**
     * Evaluated after the JRPIP method is evaluated, and throws an execution. Any exception thrown on this method
     * will replace the original exception thrown by the JRPIP method with the exception thrown on this method.
     *
     * This method wont be executed if the exception was thrown when executing, {@link #afterMethodEvaluationFinishes(JrpipRequestContext, java.lang.reflect.Method, Object[], Object)}.
     * This means only one afterMethod will be executed for every request, and not both!
     *
     * @param context context of the JRPIP method requests been intercepted
     * @param method JRPIP method been executed
     * @param arguments arguments for the JRPIP method evaluation
     * @param exception exception thrown when evaluating the JRPIP method
     */
    void afterMethodEvaluationFails(JrpipRequestContext context, Method method, Object[] arguments, Throwable exception);
}
