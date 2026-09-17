/// Shell resolution candidate with executable and default launch arguments
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ResolvedShell {
    pub executable: String,
    pub arguments: Vec<String>,
}

/// Resolves the preferred shell for Windows ConPTY execution.
///
/// Hierarchy:
/// 1. Explicit override passed via CLI `--shell`
/// 2. `SHELL` environment variable
/// 3. PowerShell 7 (`pwsh.exe`)
/// 4. Windows PowerShell (`powershell.exe`)
/// 5. Command Prompt (`%COMSPEC%` or `cmd.exe`)
pub fn resolve_windows_shell(explicit: Option<&str>) -> ResolvedShell {
    if let Some(sh) = explicit {
        let trimmed = sh.trim();
        let args = if is_powershell(trimmed) {
            vec![
                "-NoLogo".to_string(),
                "-ExecutionPolicy".to_string(),
                "Bypass".to_string(),
            ]
        } else {
            Vec::new()
        };
        return ResolvedShell {
            executable: trimmed.to_string(),
            arguments: args,
        };
    }

    if let Ok(sh) = std::env::var("SHELL") {
        if !sh.trim().is_empty() {
            let trimmed = sh.trim();
            let args = if is_powershell(trimmed) {
                vec![
                    "-NoLogo".to_string(),
                    "-ExecutionPolicy".to_string(),
                    "Bypass".to_string(),
                ]
            } else {
                Vec::new()
            };
            return ResolvedShell {
                executable: trimmed.to_string(),
                arguments: args,
            };
        }
    }

    #[cfg(target_os = "windows")]
    {
        // Try locating PowerShell 7 Core first
        if let Ok(program_files) = std::env::var("ProgramFiles") {
            let pwsh_path = format!(r"{}\PowerShell\7\pwsh.exe", program_files);
            if std::path::Path::new(&pwsh_path).exists() {
                return ResolvedShell {
                    executable: pwsh_path,
                    arguments: vec![
                        "-NoLogo".to_string(),
                        "-ExecutionPolicy".to_string(),
                        "Bypass".to_string(),
                    ],
                };
            }
        }

        // Try locating standard Windows PowerShell
        if let Ok(system_root) = std::env::var("SystemRoot") {
            let win_powershell = format!(
                r"{}\System32\WindowsPowerShell\v1.0\powershell.exe",
                system_root
            );
            if std::path::Path::new(&win_powershell).exists() {
                return ResolvedShell {
                    executable: win_powershell,
                    arguments: vec![
                        "-NoLogo".to_string(),
                        "-ExecutionPolicy".to_string(),
                        "Bypass".to_string(),
                    ],
                };
            }
        }

        // Fallback to ComSpec / cmd.exe
        if let Ok(comspec) = std::env::var("COMSPEC") {
            return ResolvedShell {
                executable: comspec,
                arguments: Vec::new(),
            };
        }
    }

    // Default cross-platform fallback
    ResolvedShell {
        executable: "powershell.exe".to_string(),
        arguments: vec![
            "-NoLogo".to_string(),
            "-ExecutionPolicy".to_string(),
            "Bypass".to_string(),
        ],
    }
}

fn is_powershell(executable: &str) -> bool {
    let lower = executable.to_lowercase();
    lower.ends_with("powershell.exe")
        || lower.ends_with("pwsh.exe")
        || lower == "powershell"
        || lower == "pwsh"
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_explicit_powershell_attaches_bypass_flags() {
        let shell = resolve_windows_shell(Some("pwsh.exe"));
        assert_eq!(shell.executable, "pwsh.exe");
        assert_eq!(
            shell.arguments,
            vec![
                "-NoLogo".to_string(),
                "-ExecutionPolicy".to_string(),
                "Bypass".to_string()
            ]
        );
    }

    #[test]
    fn test_explicit_cmd_has_no_powershell_flags() {
        let shell = resolve_windows_shell(Some("cmd.exe"));
        assert_eq!(shell.executable, "cmd.exe");
        assert!(shell.arguments.is_empty());
    }

    #[test]
    fn test_is_powershell_detection() {
        assert!(is_powershell("pwsh"));
        assert!(is_powershell("pwsh.exe"));
        assert!(is_powershell(r"C:\Program Files\PowerShell\7\pwsh.exe"));
        assert!(is_powershell("powershell.exe"));
        assert!(is_powershell(
            r"C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe"
        ));
        assert!(!is_powershell("cmd.exe"));
        assert!(!is_powershell("bash"));
    }
}
