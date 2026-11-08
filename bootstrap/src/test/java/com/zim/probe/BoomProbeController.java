package com.zim.probe;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * A route that fails with an unexpected error, for SecurityDefaultsIT. It lives
 * outside {@code com.zim.quine} on purpose: the application scans that package
 * (test classes included), and this route must exist only where the test
 * registers it by hand.
 */
@Controller
@RequestMapping("/api/probe")
public class BoomProbeController {

  @GetMapping("/boom")
  @ResponseBody
  String boom() {
    throw new IllegalStateException("unexpected server error");
  }
}
