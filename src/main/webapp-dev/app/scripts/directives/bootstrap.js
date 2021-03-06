(function (window, angular, undefined) {
  'use strict';

  angular.module('brDirectives')
    .directive('bsTooltips', function () {
      return function (scope, element) {
        element.find('[data-toggle=tooltip]').tooltip();
      };
    });
})(window, window.angular);
